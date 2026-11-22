package com.fitflex.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;

/**
 * A weekly recurring window when a trainer is free, e.g. MONDAY 07:00-10:00.
 * One-on-one bookings must fall completely inside one of these slots.
 */
@Entity
@Table(name = "availability_slots")
public class AvailabilitySlot {

    /**
     * Monday first, then by start time. Why sort in Java: the day is stored as text, so an SQL
     * ORDER BY would sort alphabetically (FRIDAY before MONDAY). DayOfWeek's own order is Mon..Sun.
     * (static fields are not saved to the database.)
     */
    public static final Comparator<AvailabilitySlot> WEEK_ORDER =
            Comparator.comparing(AvailabilitySlot::getDayOfWeek).thenComparing(AvailabilitySlot::getStartTime);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private Trainer trainer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    // ----- getters and setters -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public void setTrainer(Trainer trainer) {
        this.trainer = trainer;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
