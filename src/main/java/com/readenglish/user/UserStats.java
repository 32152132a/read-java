package com.readenglish.user;

public record UserStats(
    int learnedUnits, int totalUnits, int streakDays, int completedStages, int evaluationCount) {}
