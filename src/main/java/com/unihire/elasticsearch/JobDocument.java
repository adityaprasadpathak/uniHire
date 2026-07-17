package com.unihire.elasticsearch;

import com.unihire.models.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDocument {

    @Id
    private String id; // ES uses String IDs typically, we will map Job.getId().toString()

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String experience;

    @Field(type = FieldType.Keyword)
    private String salary;

    @Field(type = FieldType.Text)
    private String skills;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Keyword)
    private String department;

    @Field(type = FieldType.Keyword)
    private String employmentType;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS||uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime expiryDate;
    
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS||uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime publishedAt;

    @Field(type = FieldType.Text)
    private String companyName;

    @Field(type = FieldType.Long)
    private Long employerId;
}
