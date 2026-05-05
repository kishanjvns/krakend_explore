package com.mediq.analytics.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "doctor_performance_summary", schema = "mediq_analytics")
@IdClass(DoctorPerformanceSummaryEntity.DoctorDateId.class)
public class DoctorPerformanceSummaryEntity {

    @Id
    @Column(name = "doctor_id", nullable = false)
    private String doctorId;

    @Id
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_appointments", nullable = false)
    private int totalAppointments = 0;

    @Column(name = "completed_appointments", nullable = false)
    private int completedAppointments = 0;

    @Column(name = "cancelled_appointments", nullable = false)
    private int cancelledAppointments = 0;

    protected DoctorPerformanceSummaryEntity() {}

    public DoctorPerformanceSummaryEntity(String doctorId, LocalDate summaryDate) {
        this.doctorId = doctorId;
        this.summaryDate = summaryDate;
    }

    public String getDoctorId() { return doctorId; }
    public LocalDate getSummaryDate() { return summaryDate; }
    public int getTotalAppointments() { return totalAppointments; }
    public void setTotalAppointments(int totalAppointments) { this.totalAppointments = totalAppointments; }
    public int getCompletedAppointments() { return completedAppointments; }
    public void setCompletedAppointments(int completedAppointments) { this.completedAppointments = completedAppointments; }
    public int getCancelledAppointments() { return cancelledAppointments; }
    public void setCancelledAppointments(int cancelledAppointments) { this.cancelledAppointments = cancelledAppointments; }

    public static class DoctorDateId implements Serializable {
        private String doctorId;
        private LocalDate summaryDate;
        public DoctorDateId() {}
        public DoctorDateId(String doctorId, LocalDate summaryDate) {
            this.doctorId = doctorId;
            this.summaryDate = summaryDate;
        }
    }
}
