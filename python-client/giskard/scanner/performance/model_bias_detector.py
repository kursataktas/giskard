import pandas as pd
from sklearn import metrics
from typing import Optional, Sequence

from ...models.base import BaseModel
from ...models._precooked import PrecookedModel
from ...datasets.base import Dataset
from ...slicing.utils import get_slicer
from ...slicing.text_slicer import TextSlicer
from ...slicing.category_slicer import CategorySlicer
from ...ml_worker.testing.registry.slicing_function import SlicingFunction
from .issues import PerformanceIssue, PerformanceIssueInfo
from .metrics import PerformanceMetric, get_metric
from ..decorators import detector
from ...client.python_utils import warning


@detector(name="model_bias", tags=["model_bias", "classification", "regression"])
class ModelBiasDetector:
    def __init__(self, metrics: Optional[Sequence] = None, threshold: float = 0.1, method: str = "tree"):
        self.metrics = metrics
        self.threshold = threshold
        self.method = method

    def run(self, model: BaseModel, dataset: Dataset):
        # Check if we have enough data to run the scan
        if len(dataset) < 100:
            warning("Skipping model bias scan: the dataset is too small.")
            return []

        # Calculate loss
        meta = self._calculate_meta(model, dataset)

        # Find slices
        slices = self._find_slices(dataset.select_columns(model.meta.feature_names), meta)

        # Keep only slices of size at least 5% of the dataset
        slices = [s for s in slices if 0.05 * len(dataset) <= len(dataset.slice(s))]

        # Create issues from the slices
        issues = self._find_issues(slices, model, dataset)

        return issues

    def _calculate_meta(self, model, dataset):
        true_target = dataset.df.loc[:, dataset.target].values
        pred = model.predict(dataset)

        loss_values = [
            metrics.log_loss([true_label], [probs], labels=model.meta.classification_labels)
            for true_label, probs in zip(true_target, pred.raw)
        ]

        return pd.DataFrame({"__gsk__loss": loss_values}, index=dataset.df.index)

    def _find_slices(self, dataset: Dataset, meta: pd.DataFrame):
        df_with_meta = dataset.df.join(meta)
        target_col = "__gsk__loss"

        # @TODO: Handle this properly once we have support for metadata in datasets
        column_types = dataset.column_types.copy()
        column_types["__gsk__loss"] = "numeric"
        dataset_with_meta = Dataset(df_with_meta, target=dataset.target, column_types=column_types)

        # Columns by type
        cols_by_type = {
            type_val: [col for col, col_type in dataset.column_types.items() if col_type == type_val]
            for type_val in ["numeric", "category", "text"]
        }

        # Numerical features
        slicer = get_slicer(self.method, dataset_with_meta, target_col)

        slices = []
        for col in cols_by_type["numeric"]:
            slices.extend(slicer.find_slices([col]))

        # Categorical features
        slicer = CategorySlicer(dataset_with_meta, target=target_col)
        for col in cols_by_type["category"]:
            slices.extend(slicer.find_slices([col]))

        # @TODO: FIX THIS
        # Text features
        slicer = TextSlicer(dataset_with_meta, target=target_col, slicer=self.method)
        for col in cols_by_type["text"]:
            slices.extend(slicer.find_slices([col]))

        return slices

    def _find_issues(
        self,
        slices: Sequence[SlicingFunction],
        model: BaseModel,
        dataset: Dataset,
    ) -> Sequence[PerformanceIssue]:
        # Use a precooked model to speed up the tests
        precooked = PrecookedModel.from_model(model, dataset)
        detector = IssueFinder(self.metrics, self.threshold)
        issues = detector.detect(precooked, dataset, slices)

        return issues


class IssueFinder:
    def __init__(self, metrics: Optional[Sequence] = None, threshold: float = 0.1):
        self.metrics = metrics
        self.threshold = threshold

    def detect(self, model: BaseModel, dataset: Dataset, slices: Sequence[SlicingFunction]):
        # Prepare metrics
        metrics = self._get_default_metrics(model) if self.metrics is None else self.metrics
        metrics = [get_metric(m) for m in metrics]

        issues = []

        for metric in metrics:
            issues.extend(self._detect_for_metric(model, dataset, slices, metric))

        return issues

    def _get_default_metrics(self, model: BaseModel):
        if model.is_classification:
            return ["accuracy", "f1", "precision", "recall"]

        return ["mse"]

    def _detect_for_metric(
        self, model: BaseModel, dataset: Dataset, slices: Sequence[SlicingFunction], metric: PerformanceMetric
    ):
        # Calculate the metric on the reference dataset
        ref_metric_val = metric(model, dataset)

        # Now we calculate the metric on each slice and compare it to the reference
        issues = []
        for slice_fn in slices:
            sliced_dataset = dataset.slice(slice_fn)
            metric_val = metric(model, sliced_dataset)
            relative_delta = (metric_val - ref_metric_val) / ref_metric_val

            if metric.greater_is_better:
                is_issue = relative_delta < -self.threshold
            else:
                is_issue = relative_delta > self.threshold

            if is_issue:
                level = "major" if abs(relative_delta) > 2 * self.threshold else "medium"

                issue_info = PerformanceIssueInfo(
                    slice_fn=slice_fn,
                    metric=metric,
                    metric_value_slice=metric_val,
                    metric_value_reference=ref_metric_val,
                    slice_size=len(sliced_dataset),
                )

                issues.append(
                    PerformanceIssue(
                        model,
                        dataset,
                        level=level,
                        info=issue_info,
                    )
                )

        return issues
