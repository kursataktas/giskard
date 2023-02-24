package ai.giskard.web.rest.controllers.testing;

import ai.giskard.domain.TestFunction;
import ai.giskard.domain.TestFunctionArgument;
import ai.giskard.domain.Project;
import ai.giskard.domain.ml.TestResult;
import ai.giskard.domain.ml.testing.Test;
import ai.giskard.domain.ml.testing.TestExecution;
import ai.giskard.ml.MLWorkerClient;
import ai.giskard.repository.ProjectRepository;
import ai.giskard.repository.ml.*;
import ai.giskard.service.CodeTestTemplateService;
import ai.giskard.service.GRPCMapper;
import ai.giskard.service.TestArgumentService;
import ai.giskard.service.TestService;
import ai.giskard.service.ml.MLWorkerService;
import ai.giskard.web.dto.RunAdhocTestRequest;
import ai.giskard.web.dto.TestTemplatesResponse;
import ai.giskard.web.dto.mapper.GiskardMapper;
import ai.giskard.web.dto.ml.TestDTO;
import ai.giskard.web.dto.ml.TestExecutionResultDTO;
import ai.giskard.web.dto.ml.TestSuiteDTO;
import ai.giskard.web.dto.ml.TestTemplateExecutionResultDTO;
import ai.giskard.web.rest.errors.Entity;
import ai.giskard.web.rest.errors.EntityNotFoundException;
import ai.giskard.worker.RunAdHocTestRequest;
import ai.giskard.worker.TestResultMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static ai.giskard.web.rest.errors.Entity.TEST_FUNCTION;
import static ai.giskard.web.rest.errors.Entity.TEST_SUITE;


@RestController
@RequestMapping("/api/v2/testing/tests")
@RequiredArgsConstructor
public class TestController {
    private final TestRepository testRepository;
    private final TestService testService;
    private final TestSuiteRepository testSuiteRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final CodeTestTemplateService codeTestTemplateService;
    private final MLWorkerService mlWorkerService;
    private final ProjectRepository projectRepository;
    private final DatasetRepository datasetRepository;
    private final ModelRepository modelRepository;
    private final TestFunctionRepository testFunctionRepository;
    private final GRPCMapper grpcMapper;
    private final TestArgumentService testArgumentService;
    private final GiskardMapper giskardMapper;

    @GetMapping("")
    public List<TestDTO> getTests(@RequestParam Long suiteId) {
        return testRepository.findAllByTestSuiteId(suiteId).stream().map(test -> {
            TestDTO res = new TestDTO(test);
            Optional<TestExecution> exec = testExecutionRepository.findFirstByTestIdOrderByExecutionDateDesc(test.getId());
            exec.ifPresent(testExecution -> {
                res.setStatus(testExecution.getResult());
                res.setLastExecutionDate(testExecution.getExecutionDate());
            });
            return res;
        }).toList();
    }

    @GetMapping("/{testId}")
    public TestDTO getTest(@PathVariable() Long testId) {
        Optional<Test> test = testRepository.findById(testId);
        if (test.isPresent()) {
            return new TestDTO(test.get());
        } else {
            throw new EntityNotFoundException(Entity.TEST, testId);
        }
    }

    @DeleteMapping("/{testId}")
    public TestSuiteDTO deleteTest(@PathVariable() Long testId) {
        return giskardMapper.testSuiteToTestSuiteDTO(testService.deleteTest(testId));
    }

    @PostMapping("")
    public TestDTO createTest(@Valid @RequestBody TestDTO dto) {
        Test test = new Test();
        test.setName(dto.getName());
        testSuiteRepository.findById(dto.getSuiteId()).ifPresentOrElse(test::setTestSuite, () -> {
            throw new EntityNotFoundException(TEST_SUITE, dto.getSuiteId());
        });

        Test savedTest = testRepository.save(test);
        return new TestDTO(savedTest);
    }


    @PostMapping("/{testId}/run")
    public TestExecutionResultDTO runTest(@PathVariable() Long testId) throws IOException {
        return testService.runTest(testId);
    }

    @PutMapping("")
    public Optional<TestDTO> saveTest(@RequestBody TestDTO dto) {
        return testService.saveTest(dto);
    }

    @GetMapping("/code-test-templates")
    @Transactional
    public TestTemplatesResponse getCodeTestTemplates(@RequestParam(value = "suiteId", required = true) Long suiteId) {
        return codeTestTemplateService.getTemplates(suiteId);
    }

    @PostMapping("/run-test")
    @Transactional
    public TestTemplateExecutionResultDTO runAdHocTest(@RequestBody RunAdhocTestRequest request) {
        TestFunction testFunction = testFunctionRepository.findById(UUID.fromString(request.getTestUuid()))
            .orElseThrow(() -> new EntityNotFoundException(TEST_FUNCTION, request.getTestUuid().toString()));

        try (MLWorkerClient client = mlWorkerService.createClient(projectRepository.getById(request.getProjectId()).isUsingInternalWorker())) {
            Map<String, String> argumentTypes = testFunction.getArgs().stream()
                .collect(Collectors.toMap(TestFunctionArgument::getName, TestFunctionArgument::getType));

            RunAdHocTestRequest.Builder builder = RunAdHocTestRequest.newBuilder()
                .setTestUuid(request.getTestUuid().toString());

            for (Map.Entry<String, String> entry : request.getInputs().entrySet()) {
                builder.addArguments(testArgumentService.buildTestArgument(argumentTypes, entry.getKey(), entry.getValue(), project.getKey()));
            }

            TestResultMessage testResultMessage = client.getBlockingStub().runAdHocTest(builder.build());
            TestTemplateExecutionResultDTO res = new TestTemplateExecutionResultDTO(testFunction.getUuid());
            res.setResult(testResultMessage);
            if (testResultMessage.getResultsList().stream().anyMatch(r -> !r.getResult().getPassed())) {
                res.setStatus(TestResult.FAILED);
            } else {
                res.setStatus(TestResult.PASSED);
            }
            return res;
        }

        //TestRegistryResponse response = mlWorkerService.createClient().getBlockingStub().getTestRegistry(Empty.newBuilder().build());

        //return JsonFormat.printer().print(response);
    }
}
