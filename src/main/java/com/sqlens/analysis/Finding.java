package com.sqlens.analysis;

/**
 * One problem (or noteworthy fact) spotted in a query plan.
 *
 * @param table      table the finding refers to, or null for plan-wide findings
 * @param problem    what is wrong, in plain words
 * @param suggestion what to try
 */
public record Finding(Severity severity, String table, String problem, String suggestion) {}
