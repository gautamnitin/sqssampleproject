package com.sqs.sqsproject.sqs;

import com.sqs.sqsproject.employee.Employee;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sqs/employees")
@RequiredArgsConstructor
public class EmployeePublisherController {

    private final SqsTemplate sqsTemplate;
    private final SqsProperties props;

    @PostMapping
    public ResponseEntity<Void> publishOne(@Valid @RequestBody Employee employee) {
        sqsTemplate.send(builder -> builder.queue(props.getEmployeeQueue()).payload(employee));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> publishBatch(@RequestBody @NotEmpty List<@Valid Employee> employees) {
        for (Employee e : employees) {
            sqsTemplate.send(builder -> builder.queue(props.getEmployeeQueue()).payload(e));
        }
        return ResponseEntity.accepted().build();
    }
}
