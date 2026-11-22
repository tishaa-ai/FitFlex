package com.fitflex.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A generated one-day diet plan plus the calorie numbers it was built from.
 * The meals themselves are stored as JSON text in {@code planJson}.
 */
@Entity
@Table(name = "diet_plans")
public class DietPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FitnessGoal goal;

    /** vegetarian / vegan / eggetarian / non-veg */
    @Column(length = 20)
    private String dietType;

    private Integer mealsPerDay;

    /** Basal Metabolic Rate (Mifflin-St Jeor), kcal/day. */
    private Double bmr;

    /** Total Daily Energy Expenditure = BMR x activity factor, kcal/day. */
    private Double tdee;

    private Integer targetCalories;

    /** @Lob + LONGTEXT so long JSON fits in MySQL (VARCHAR would be too small). */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String planJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DietSource source;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Fills the timestamp automatically just before the row is first inserted. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

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

    public FitnessGoal getGoal() {
        return goal;
    }

    public void setGoal(FitnessGoal goal) {
        this.goal = goal;
    }

    public String getDietType() {
        return dietType;
    }

    public void setDietType(String dietType) {
        this.dietType = dietType;
    }

    public Integer getMealsPerDay() {
        return mealsPerDay;
    }

    public void setMealsPerDay(Integer mealsPerDay) {
        this.mealsPerDay = mealsPerDay;
    }

    public Double getBmr() {
        return bmr;
    }

    public void setBmr(Double bmr) {
        this.bmr = bmr;
    }

    public Double getTdee() {
        return tdee;
    }

    public void setTdee(Double tdee) {
        this.tdee = tdee;
    }

    public Integer getTargetCalories() {
        return targetCalories;
    }

    public void setTargetCalories(Integer targetCalories) {
        this.targetCalories = targetCalories;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public DietSource getSource() {
        return source;
    }

    public void setSource(DietSource source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
