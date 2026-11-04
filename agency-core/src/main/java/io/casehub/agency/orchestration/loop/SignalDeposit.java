package io.casehub.agency.orchestration.loop;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record SignalDeposit(String name, double strength, @Nullable Duration halfLife) {}
