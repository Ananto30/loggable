package loggable;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Azizul Haque Ananto
 */

@Aspect
@Slf4j
public class LoggerAspect {

    @Around("@annotation(Loggable)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {

        long start = System.currentTimeMillis();
        var result = joinPoint.proceed();
        if (result instanceof Mono) {
            return logMonoResult(joinPoint, start, (Mono) result);
        } else if (result instanceof Flux) {
            return logFluxResult(joinPoint, start, (Flux) result);
        } else {
            // body type is not Mono/Flux
            logResult(joinPoint, start, result);
            return result;
        }

    }

    private Mono logMonoResult(ProceedingJoinPoint joinPoint, long start, Mono result) {
        AtomicReference<String> traceId = new AtomicReference<>("");
        return result
                .doOnSuccess(o -> {
                    setTraceIdInMDC(traceId);
                    var response = Objects.nonNull(o) ? o.toString() : "";
                    logEntry(joinPoint);
                    logSuccessExit(joinPoint, start, response);
                })
                .subscriberContext(context -> {
                    // the error happens in a different thread, so get the trace from context, set in MDC and downstream to doOnError
                    setTraceIdFromContext(traceId, (Context) context);
                    return context;
                })
                .doOnError(o -> {
                    setTraceIdInMDC(traceId);
                    logEntry(joinPoint);
                    logErrorExit(joinPoint, start, o.toString());
                });
    }

    private Flux logFluxResult(ProceedingJoinPoint joinPoint, long start, Flux result) {
        return result
                .doFinally(o -> {
                    logEntry(joinPoint);
                    logSuccessExit(joinPoint, start, o.toString()); // NOTE: this is costly
                })
                .doOnError(o -> {
                    logEntry(joinPoint);
                    logErrorExit(joinPoint, start, o.toString());
                });
    }

    private void logResult(ProceedingJoinPoint joinPoint, long start, Object result) {
        try {
            logEntry(joinPoint);
            logSuccessExit(joinPoint, start, result.toString());
        } catch (Exception e) {
            logEntry(joinPoint);
            logErrorExit(joinPoint, start, e.getMessage());
        }
    }


    private void logErrorExit(ProceedingJoinPoint joinPoint, long start, String error) {
        log.error("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                joinPoint.getArgs()[0],
                error, (System.currentTimeMillis() - start));
    }

    private void logSuccessExit(ProceedingJoinPoint joinPoint, long start, String response) {
        log.info("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                joinPoint.getArgs()[0],
                response, (System.currentTimeMillis() - start));
    }

    private void logEntry(ProceedingJoinPoint joinPoint) {
        log.info("Enter: {}.{}() with argument[s] = {}",
                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                joinPoint.getArgs());
    }

    private void setTraceIdFromContext(AtomicReference<String> traceId, Context context) {
        if (context.hasKey("X-B3-TraceId")) {
            traceId.set(context.get("X-B3-TraceId"));
            setTraceIdInMDC(traceId);
        }
    }

    private void setTraceIdInMDC(AtomicReference<String> traceId) {
        if (!traceId.get().isEmpty()) {
            MDC.put("X-B3-TraceId", traceId.get());
        }
    }

}