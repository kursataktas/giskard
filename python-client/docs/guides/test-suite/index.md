# 🧪 Test your ML model

:::{warning}
First you'll need to create a Model and a dataset (And scan your model),
see [🔬 Scan your ML model](../scan/index.rst)
:::

## 1. Install the Giskard library

In order to test your model, you'll need to install the `giskard` library with `pip`:

::::{tab-set}
:::{tab-item} Windows

```sh
pip install "giskard[scan] @ git+https://github.com/Giskard-AI/giskard.git@task/GSK-1000-robustness-numerical#subdirectory=python-client" --user
```

:::

:::{tab-item} Mac and Linux

```sh
pip install "giskard[scan] @ git+https://github.com/Giskard-AI/giskard.git@task/GSK-1000-robustness-numerical#subdirectory=python-client"
```

:::
::::

## 2. Execute a Giskard test

:::{hint}
You can see all our tests in the [📖 Test Catalog](../../catalogs/test-catalog/index.rst)
:::

::::{tab-set}
:::{tab-item} Drift tests

```python
from giskard import demo, Model, Dataset, testing

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")
train_df = Dataset(df=df.head(400), target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])
test_df = Dataset(df=df.tail(400), target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

result = testing.test_drift_prediction_ks(model=wrapped_model, actual_dataset=test_df, reference_dataset=train_df,
                                          classification_label='yes', threshold=0.5).execute()

print("Result for 'Classification Probability drift (Kolmogorov-Smirnov):")
print(f"Passed: {result.passed}")
print(f"Metric: {result.metric}")
```

**Description:**

&#x20;In order to execute the test provided by Giskard. You first need to wrap your dataset and model into Giskard's
one. Then you need to initialize the test and execute it, it will return a **TestResult** or a **bool**.

:::

:::{tab-item} Performance tests

```python
from giskard import demo, Model, Dataset, testing

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")
wrapped_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

result = testing.test_f1(dataset=wrapped_dataset, model=wrapped_model).execute()


print(f"result: {result.passed} with metric {result.metric}")
```

:::

:::{tab-item} Metamorphic tests

```python
from giskard import demo, Model, Dataset, testing, transformation_function

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")
wrapped_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

@transformation_function
def add_three_years(row):
    row['Age'] = row['Age'] + 3
    return row

result = testing.test_metamorphic_invariance(model=wrapped_model,
                                             dataset=wrapped_dataset,
                                             transformation_function=add_three_years
                                             ).execute()

print(f"result: {result.passed} with metric {result.metric}")
```

See [🔪 Create slices and transformations function / Transformation](../../guides/slice/index.md)
to see how to create custom transformations

:::

:::{tab-item} Statistic tests

```python
from giskard import demo, Model, Dataset, testing

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")
wrapped_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

result = testing.test_right_label(wrapped_model, wrapped_dataset, 'yes').execute()
print(f"result: {result.passed} with metric {result.metric}")
```

:::
::::

## 3. Create & Execute a test suite

A test suite is a collection of tests that can be parameterized to accommodate various scenarios. Each test within the
suite may have some parameters left unspecified. When executing the test suite, you can provide the missing parameters
through the run method. This allows for flexible and customizable test execution based on your specific needs.
::::{tab-set}

:::{tab-item} Model as input
Example using a two performance tests

```python
from giskard import demo, Model, Dataset, testing, Suite

model, df = demo.titanic()

wrapped_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

# Create a suite and add a F1 test and an accuracy test
# Note that all the parameters are specified except model
# Which means that we will need to specify model everytime we run the suite
suite = Suite() \
    .add_test(testing.test_f1(dataset=wrapped_dataset)) \
    .add_test(testing.test_accuracy(dataset=wrapped_dataset))

# Create our first model
my_first_model = Model(model=model, model_type="classification")

# Run the suite by specifying our model and display the results
passed, results = suite.run(model=my_first_model)

# Create an improved version of our model
my_improved_model = Model(model=model, model_type="classification")

# Run the suite with our new version and check if the results improved
suite.run(model=my_improved_model)
```

#### Description

In this example we create a Suite with two tests, `test_f1` and `test_accuracy`. We specified all the parameters expect
the dataset to "expose" it as a run input. We can see that the way to set parameters differ whenever we are dealing with
a test class or a test function.

:::

:::{tab-item} Dataset as input
```python
import pandas as pd
from giskard import demo, Model, Dataset, testing, Suite, transformation_function, slicing_function

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")

@transformation_function
def transform(df: pd.Series) -> pd.Series:
    df['Age'] = df['Age'] + 10
    return df

@slicing_function(row_level=False, name='female')
def slice_female(df: pd.DataFrame) -> pd.DataFrame:
    return df[df.Sex == 'female']

@slicing_function(row_level=False, name='male')
def slice_male(df: pd.DataFrame) -> pd.DataFrame:
    return df[df.Sex == 'male']

# Create a suite and add a disparate impact test and a metamorphic test
# Note that all the parameters are specified except dataset
# Which means that we will need to specify dataset everytime we run the suite
suite = Suite() \
    .add_test(testing.test_disparate_impact(model=wrapped_model, protected_slicing_function=slice_female,
                                                       unprotected_slicing_function=slice_male, positive_outcome="yes")) \
    .add_test(testing.test_metamorphic_invariance(model=wrapped_model, transformation_function=transform))

# Create our first dataset
my_first_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

# Run the suite by specifying our model and display the results
passed, results = suite.run(dataset=my_first_dataset)

# Create an updated version of the dataset
my_updated_dataset =  Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

# Run the suite with our new version and check if the results improved
suite.run(dataset=my_updated_dataset)
```
:::

:::{tab-item} Shared test input
```python
from giskard import demo, Model, Dataset, testing, Suite, SuiteInput, slicing_function
import pandas as pd

model, df = demo.titanic()

wrapped_model = Model(model=model, model_type="classification")
wrapped_dataset = Dataset(df=df, target="Survived", cat_columns=['Pclass', 'Sex', "SibSp", "Parch", "Embarked"])

@slicing_function(row_level=False, name='female')
def slice_female(df: pd.DataFrame) -> pd.DataFrame:
    return df[df.Sex == 'female']

sliced_dataset = wrapped_dataset.slice(slice_female)

shared_input = SuiteInput("dataset", Dataset)

suite = Suite() \
    .add_test(testing.test_auc(dataset=shared_input, threshold=0.2)) \
    .add_test(testing.test_f1(dataset=shared_input, threshold=0.2)) \
    .add_test(testing.test_diff_f1(threshold=0.2, actual_dataset=shared_input))

suite.run(model=wrapped_model, dataset=wrapped_dataset, reference_dataset=sliced_dataset)
```
:::
::::

## 4. Create a custom test

::::{tab-set}
:::{tab-item} Using function

```python
from giskard import test, Dataset, TestResult

@test(name="Custom Test Example", tags=["quality", "custom"])
def uniqueness_test_function(dataset: Dataset,
                             column_name: str = None,
                             category: str = None,
                             threshold: float = 0.5):
    freq_of_cat = dataset.df[column_name].value_counts()[category] / (len(dataset.df))
    passed = freq_of_cat < threshold

    return TestResult(passed=passed, metric=freq_of_cat)
```

#### Description

In order to define a custom test function, you just need to declare a method with its parameters and return a result.
It's pretty simple, however, it does not allow autocompletion during the test suite creation, contrary to the
class-based method.

#### Usage \[Reference]

* <mark style="color:red;">**`parameters`**</mark> : **Your parameters need to have a type defined.** Here is the type
  allowed as your test parameters:
    * `Dataset` A giskard dataset, [wrap your dataset](../wrap_dataset/index.md)
    * `BaseModel` A giskard model, [wrap your model](../wrap_model/index.md)
    * `int/float/bool/str`  Any primitive type can be used
* <mark style="color:red;">**`return`**</mark> The result of your test must be either a bool or a TestResult:
    * `bool` Either `True` if the test passed or `False` if it failed
    * `TestResult` An object containing more details:

        * `passed` A required bool to know if the test passed
        * `metric` A float value with the score of the test

#### Set metadata to your test

In order to **set metadata** to your test, you need to use the `@test` decorator before your method or your class

* <mark style="color:red;">**`name`**</mark> : A custom name that will be visible in the application
* <mark style="color:red;">**`tags`**</mark> : A list of tags that allow you to quickly identify your tests
  :::

:::{tab-item} Using test class

```python
from giskard import GiskardTest, Dataset, TestResult


class DataQuality(GiskardTest):

    def __init__(self,
                 dataset: Dataset = None,
                 threshold: float = 0.5,
                 column_name: str = None,
                 category: str = None):
        self.dataset = dataset
        self.threshold = threshold
        self.column_name = column_name
        self.category = category
        super().__init__()

    def execute(self) -> TestResult:
        freq_of_cat = self.dataset.df[self.column_name].value_counts()[self.category] / (len(self.dataset.df))
        passed = freq_of_cat < self.threshold

        return TestResult(passed=passed, metric=freq_of_cat)
```

#### Description

In order to define a custom test class, you need to extends `GiskardTest` and implement the `execute` method

#### Main methods \[Reference]

* <mark style="color:red;">**`__init__`**</mark> : The initialisation method must be implemented in order to specify the
  required parameters of your test. **It is also required to call the parent initialization method**
  calling `super().__init__()`. **Your parameters need to have a type and default value specified.** You can should use
  **None** as a default value if you require a parameter to be specified. Here is the type allowed in the init method:
    * `Dataset` A giskard dataset, [wrap your dataset](../wrap_dataset/index.md)
    * `BaseModel` A giskard model, [wrap your model](../wrap_model/index.md)
    * `int/float/bool/str`  Any primitive type can be used
* <mark style="color:red;">**`execute`**</mark> The execute method will be called to perform the test, you will be able
  to access all the parameters set by the initialization method. Your method can return two type of results:
    * `bool` Either `True` if the test passed or `False` if it failed
    * `TestResult` An object containing more details:
        * `passed` A required bool to know if the test passed
        * `metric` A float value with the score of the test

:::
::::

:::{hint}
To upload your test suite to the Giskard server, go to [Upload objects](../../guides/upload/index.md) to the Giskard server.
:::