package com.fitflex.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * One day's progress log. Manual entries usually hold weight/minutes; entries from the
 * pose detection script also hold the exercise, reps and correct-form percentage.
 */
@Entity
@Table(name = "progress_entries")
public class ProgressEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    private Double weightKg;

    private Integer workoutMinutes;

    private Integer caloriesBurned;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgressSource source;

    @Column(length = 50)
    private String exercise;

    private Integer reps;

    /** 0-100: share of frames in which the posture was correct. */
    private Double correctFormPercent;

    // ----- getters and setters -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getWorkoutMinutes() {
        return workoutMinutes;
    }

    public void setWorkoutMinutes(Integer workoutMinutes) {
        this.workoutMinutes = workoutMinutes;
    }

    public Integer getCaloriesBurned() {
        return caloriesBurned;
    }

    public void setCaloriesBurned(Integer caloriesBurned) {
        this.caloriesBurned = caloriesBurned;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ProgressSource getSource() {
        return source;
    }

    public void setSource(ProgressSource source) {
        this.source = source;
    }

    public String getExercise() {
        return exercise;
    }

    public void setExercise(String exercise) {
        this.exercise = exercise;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Double getCorrectFormPercent() {
        return correctFormPercent;
    }

    public void setCorrectFormPercent(Double correctFormPercent) {
        this.correctFormPercent = correctFormPercent;
    }
}
