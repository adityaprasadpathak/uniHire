package com.unihire.services.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihire.dto.ExperienceSection;
import com.unihire.dto.EducationSection;
import com.unihire.dto.ResumeVersionDataDto;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.ResumeVersion;
import com.unihire.repositories.ResumeVersionRepository;
import com.unihire.utils.DateUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Service
public class ResumeExportService {

    @Autowired
    private ResumeVersionRepository versionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void export(Long versionId, Long candidateId, String format, HttpServletResponse response) throws IOException {
        ResumeVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
        if (!version.getCandidateProfile().getId().equals(candidateId)) {
            throw new UnauthorizedOperationException("Access denied to version: " + versionId);
        }
        if (version.getVersionDataJson() == null) {
            throw new ResourceNotFoundException("Version is still processing — no data available yet");
        }

        ResumeVersionDataDto data = objectMapper.readValue(version.getVersionDataJson(), ResumeVersionDataDto.class);
        String sanitizedName = (data.getCandidateName() != null ? data.getCandidateName().replace(" ", "_") : "Resume");

        switch (format.toUpperCase()) {
            case "JSON" -> exportAsJson(data, sanitizedName, response);
            case "DOCX" -> exportAsDocx(data, sanitizedName, version.getVersionName(), response);
            default -> exportAsHtml(data, sanitizedName, version.getVersionName(), response);
        }
    }

    private void exportAsJson(ResumeVersionDataDto data, String name, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=" + name + "_Resume.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), data);
    }

    private void exportAsDocx(ResumeVersionDataDto data, String fileName, String versionName, HttpServletResponse response) throws IOException {
        response.setContentType("application/msword");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName + "_Resume.doc");
        // We output HTML which Word natively understands and renders flawlessly
        String content = buildHtmlContent(data, versionName);
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }


    private void exportAsHtml(ResumeVersionDataDto data, String fileName, String versionName, HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName + "_Resume.html");
        String html = buildHtmlContent(data, versionName);
        PrintWriter writer = response.getWriter();
        writer.write(html);
        writer.flush();
    }

    private String buildTextContent(ResumeVersionDataDto data, String versionName) {
        StringBuilder sb = new StringBuilder();
        sb.append(safeStr(data.getCandidateName())).append("\n");
        if (data.getEmail() != null) sb.append(data.getEmail()).append(" | ");
        if (data.getPhone() != null) sb.append(data.getPhone());
        sb.append("\n\n");
        if (data.getProfessionalSummary() != null) {
            sb.append("PROFESSIONAL SUMMARY\n").append("=".repeat(40)).append("\n");
            sb.append(data.getProfessionalSummary()).append("\n\n");
        }
        if (data.getSkills() != null && !data.getSkills().isEmpty()) {
            sb.append("SKILLS\n").append("=".repeat(40)).append("\n");
            sb.append(String.join(", ", data.getSkills())).append("\n\n");
        }
        if (data.getExperiences() != null) {
            sb.append("EXPERIENCE\n").append("=".repeat(40)).append("\n");
            for (ExperienceSection exp : data.getExperiences()) {
                sb.append(safeStr(exp.getJobTitle())).append(" at ").append(safeStr(exp.getCompany())).append("\n");
                sb.append(DateUtils.formatDateRange(exp.getStartDate(), exp.getEndDate())).append("\n");
                if (exp.getBullets() != null) {
                    exp.getBullets().forEach(b -> sb.append("• ").append(b).append("\n"));
                }
                sb.append("\n");
            }
        }
        if (data.getEducations() != null) {
            sb.append("EDUCATION\n").append("=".repeat(40)).append("\n");
            for (EducationSection edu : data.getEducations()) {
                sb.append(safeStr(edu.getDegree())).append(" - ").append(safeStr(edu.getInstitution())).append("\n");
                sb.append(DateUtils.formatDateRange(edu.getStartDate(), edu.getEndDate())).append("\n\n");
            }
        }
        if (data.getCertifications() != null && !data.getCertifications().isEmpty()) {
            sb.append("CERTIFICATIONS\n").append("=".repeat(40)).append("\n");
            data.getCertifications().forEach(c -> sb.append("• ").append(c).append("\n"));
        }
        return sb.toString();
    }

    private String buildHtmlContent(ResumeVersionDataDto data, String versionName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>").append(safeStr(data.getCandidateName())).append(" — Resume</title>");
        sb.append("<style>");
        sb.append("body{font-family:'Helvetica Neue',sans-serif;max-width:800px;margin:40px auto;color:#222;line-height:1.6}");
        sb.append("h1{font-size:28px;margin-bottom:4px}");
        sb.append("h2{font-size:16px;border-bottom:2px solid #333;padding-bottom:4px;margin-top:24px;text-transform:uppercase}");
        sb.append(".contact{color:#555;font-size:14px;margin-bottom:20px}");
        sb.append(".exp-title{font-weight:bold}.exp-company{color:#555}.exp-date{color:#888;font-size:13px}");
        sb.append("ul{margin-top:4px;padding-left:20px}li{margin-bottom:4px;font-size:14px}");
        sb.append("@media print{body{margin:20px}}");
        sb.append("</style></head><body>");

        sb.append("<h1>").append(safeStr(data.getCandidateName())).append("</h1>");
        sb.append("<div class='contact'>");
        if (data.getEmail() != null) sb.append(data.getEmail());
        if (data.getEmail() != null && data.getPhone() != null) sb.append(" | ");
        if (data.getPhone() != null) sb.append(data.getPhone());
        if (data.getLocation() != null) sb.append(" | ").append(data.getLocation());
        sb.append("</div>");

        if (data.getProfessionalSummary() != null) {
            sb.append("<h2>Professional Summary</h2><p>").append(data.getProfessionalSummary()).append("</p>");
        }

        if (data.getSkills() != null && !data.getSkills().isEmpty()) {
            sb.append("<h2>Skills</h2><p>").append(String.join(" &bull; ", data.getSkills())).append("</p>");
        }

        if (data.getExperiences() != null && !data.getExperiences().isEmpty()) {
            sb.append("<h2>Experience</h2>");
            for (ExperienceSection exp : data.getExperiences()) {
                sb.append("<div style='margin-bottom:16px'>");
                sb.append("<span class='exp-title'>").append(safeStr(exp.getJobTitle())).append("</span>");
                sb.append(" &mdash; <span class='exp-company'>").append(safeStr(exp.getCompany())).append("</span>");
                sb.append("<div class='exp-date'>").append(DateUtils.formatDateRange(exp.getStartDate(), exp.getEndDate())).append("</div>");
                if (exp.getBullets() != null && !exp.getBullets().isEmpty()) {
                    sb.append("<ul>");
                    exp.getBullets().forEach(b -> sb.append("<li>").append(b).append("</li>"));
                    sb.append("</ul>");
                }
                sb.append("</div>");
            }
        }

        if (data.getEducations() != null && !data.getEducations().isEmpty()) {
            sb.append("<h2>Education</h2>");
            for (EducationSection edu : data.getEducations()) {
                sb.append("<div style='margin-bottom:12px'>");
                sb.append("<strong>").append(safeStr(edu.getDegree())).append("</strong>");
                sb.append(" &mdash; ").append(safeStr(edu.getInstitution()));
                sb.append("<div class='exp-date'>").append(DateUtils.formatDateRange(edu.getStartDate(), edu.getEndDate())).append("</div>");
                if (edu.getDescription() != null) {
                    sb.append("<p style='font-size:13px;color:#555'>").append(edu.getDescription()).append("</p>");
                }
                sb.append("</div>");
            }
        }

        if (data.getCertifications() != null && !data.getCertifications().isEmpty()) {
            sb.append("<h2>Certifications</h2><ul>");
            data.getCertifications().forEach(c -> sb.append("<li>").append(c).append("</li>"));
            sb.append("</ul>");
        }

        if (data.getKeywords() != null && !data.getKeywords().isEmpty()) {
            sb.append("<h2>Keywords</h2><p style='font-size:12px;color:#888'>")
              .append(String.join(", ", data.getKeywords())).append("</p>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }
}
