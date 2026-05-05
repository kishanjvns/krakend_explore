package com.mediq.emr.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_summary", schema = "mediq_emr")
public class PatientSummaryEntity {

    @Id
    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "blood_type")
    private String bloodType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergies", columnDefinition = "text[]")
    private List<String> allergies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "current_medications", columnDefinition = "text[]")
    private List<String> currentMedications = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "active_conditions", columnDefinition = "text[]")
    private List<String> activeConditions = new ArrayList<>();

    @Column(name = "last_visit_date")
    private LocalDate lastVisitDate;

    @Column(name = "primary_doctor_id")
    private String primaryDoctorId;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected PatientSummaryEntity() {}

    public PatientSummaryEntity(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientId() { return patientId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }
    public List<String> getCurrentMedications() { return currentMedications; }
    public void setCurrentMedications(List<String> currentMedications) { this.currentMedications = currentMedications; }
    public List<String> getActiveConditions() { return activeConditions; }
    public void setActiveConditions(List<String> activeConditions) { this.activeConditions = activeConditions; }
    public LocalDate getLastVisitDate() { return lastVisitDate; }
    public void setLastVisitDate(LocalDate lastVisitDate) { this.lastVisitDate = lastVisitDate; }
    public String getPrimaryDoctorId() { return primaryDoctorId; }
    public void setPrimaryDoctorId(String primaryDoctorId) { this.primaryDoctorId = primaryDoctorId; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
