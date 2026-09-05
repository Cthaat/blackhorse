package com.ruoyi.lab.task;

import java.util.function.Supplier;

/** Loads current identity from the account store; never reuses a submitted role/token snapshot. */
public interface TaskActorContext
{
    <T> T asCurrentActor(long userId, Supplier<T> action);
}
