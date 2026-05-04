package com.mediq.analytics.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_appointment_summary", schema = "mediq_analytics")
public class DailyAppointmentSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "summary_date", nullable = false, unique = true)
    private LocalDate summaryDate;

    @Column(name = "total_booked", nullable = false)
    private int totalBooked = 0;

    @Column(name = "total_confirmed", nullable = false)
    private int totalConfirmed = 0;

    @Column(name = "total_cancelled", nullable = false)
    private int totalCancelled = 0;

    @Column(name = "total_completed", nullable = false)
    private int totalCompleted = 0;

    protected DailyAppointmentSummaryEntity() {}

    public DailyAppointmentSummaryEntity(LocalDate summaryDate) {
        this.summaryDate = summaryDate;
    }

    public UUID getId() { return id; }
    public LocalDate getSummaryDate() { return summaryDate; }
    public int getTotalBooked() { return totalBooked; }
    public void setTotalBooked(int totalBooked) { this.totalBooked = totalBooked; }
    public int getTotalConfirmed() { return totalConfirmed; }
    public void setTotalConfirmed(int totalConfirmed) { this.totalConfirmed = totalConfirmed; }
    public int getTotalCancelled() { return totalCancelled; }
    public void setTotalCancelled(int totalCancelled) { this.totalCancelled = totalCancelled; }
    public int getTotalCompleted() { return totalCompleted; }
    public void setTotalCompleted(int totalCompleted) { this.totalCompleted = totalCompleted; }
}
