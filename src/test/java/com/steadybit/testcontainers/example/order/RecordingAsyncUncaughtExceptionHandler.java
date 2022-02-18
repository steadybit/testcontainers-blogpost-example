package com.steadybit.testcontainers.example.order;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordingAsyncUncaughtExceptionHandler extends SimpleAsyncUncaughtExceptionHandler {
    private final Queue<Throwable> exceptions = new LinkedBlockingQueue<>();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        super.handleUncaughtException(ex, method, params);
        this.exceptions.add(ex);
    }

    public Throwable waitFor(Class<? extends Throwable> exClass) {
        Throwable ex;
        do {
            ex = this.exceptions.peek();
        } while (!exClass.isInstance(ex));
        return ex;
    }
}
