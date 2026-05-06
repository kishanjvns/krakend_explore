package com.mediq.appointment.temporal.worker;

import com.mediq.appointment.temporal.activity.AppointmentActivitiesImpl;
import com.mediq.appointment.temporal.activity.NotificationActivitiesImpl;
import com.mediq.appointment.temporal.activity.PaymentActivitiesImpl;
import com.mediq.appointment.temporal.workflow.AppointmentBookingWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppointmentWorker {

    private static final Logger log = LoggerFactory.getLogger(AppointmentWorker.class);

    private final WorkerFactory workerFactory;
    private final AppointmentActivitiesImpl appointmentActivities;
    private final PaymentActivitiesImpl paymentActivities;
    private final NotificationActivitiesImpl notificationActivities;
    private final String taskQueue;

    public AppointmentWorker(
            WorkerFactory workerFactory,
            AppointmentActivitiesImpl appointmentActivities,
            PaymentActivitiesImpl paymentActivities,
            NotificationActivitiesImpl notificationActivities,
            @Value("${temporal.task-queue}") String taskQueue) {
        this.workerFactory = workerFactory;
        this.appointmentActivities = appointmentActivities;
        this.paymentActivities = paymentActivities;
        this.notificationActivities = notificationActivities;
        this.taskQueue = taskQueue;
    }

    @PostConstruct
    public void startWorker() {
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(AppointmentBookingWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            appointmentActivities,
            paymentActivities,
            notificationActivities);
        workerFactory.start();
        log.info("Temporal worker started on task queue: {}", taskQueue);
    }
}
