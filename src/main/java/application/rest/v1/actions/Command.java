/*
 * Copyright 2019 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package application.rest.v1.actions;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Command {
    
    public static final long DEFAULT_TIMEOUT = 30L; // 30 seconds
    public static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;
    
    private final String[] commandArgs;
    private final long timeout;
    private final TimeUnit timeoutUnit;
    
    public Command(String[] commandArgs) {
        this(commandArgs, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
    }
    
    public Command(String[] commandArgs, long timeout, TimeUnit timeoutUnit) {
        this.commandArgs = commandArgs;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
    }
    
    // Executes the command synchronously.
    public Result invoke() {
        try {
            final ProcessBuilder pb = new ProcessBuilder(commandArgs);
            final Process p = pb.start();
            if (p.waitFor(timeout, timeoutUnit)) {
                final int i = p.exitValue();
                if (i == 0) {
                    final InputStreamReader reader = new InputStreamReader(p.getInputStream());
                    final StringBuilder sb = new StringBuilder();
                    int count;
                    final char[] buffer = new char[1 << 10]; // 1K buffer
                    while ((count = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, count);
                    }
                    return Result.complete(0, sb.toString());
                }
                return Result.complete(i, null);
            }
            else {
                p.destroy();
                return Result.timedOut();
            }
        }
        catch (IOException | InterruptedException | SecurityException e) {
            return Result.failed(e);
        }
    }
    
    // Executes the command asynchronously. The result is
    // written to the Future once its available.
    public Future<Result> asyncInvoke() {
        final FutureResult result = new FutureResult();
        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(invoke());
                }
                catch (Exception e) {
                    result.setException(e);
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return result;
    }
    
    public final static class Result {
        
        public final int exitValue;
        public final String output;
        public final Status status;
        
        private Result (int exitValue, String output, Status status) {
            this.exitValue = exitValue;
            this.output = output;
            this.status = status;
        }
        
        public static Result running() {
            return new Result(0, null, Status.RUNNING);
        }
        
        public static Result complete(int exitValue, String output) {
            return new Result(exitValue, output, Status.COMPLETE);
        }
        
        public static Result timedOut() {
            return new Result(-1, null, Status.TIMED_OUT);
        }
        
        public static Result failed(Exception e) {
            return new Result(-1, null, Status.FAILED);
        }
    }
    
    public final static class FutureResult implements Future<Result> {
        
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile Result result;
        private volatile Throwable t;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return done.getCount() == 0;
        }

        @Override
        public Result get() throws InterruptedException, ExecutionException {
            done.await();
            if (t != null) {
                throw new ExecutionException(t);
            }
            return result;
        }

        @Override
        public Result get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            done.await(timeout, unit);
            if (t != null) {
                throw new ExecutionException(t);
            }
            return result;
        }
        
        void set(Result result) {
            this.result = result;
            done.countDown();
        }
        
        void setException(Throwable t) {
            this.t = t;
            done.countDown();
        }
    }
    
    public enum Status {
        RUNNING,
        COMPLETE,
        TIMED_OUT,
        FAILED
    }
}
