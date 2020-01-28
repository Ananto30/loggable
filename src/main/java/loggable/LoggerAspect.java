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
            var monoResult = (Mono) result;
            AtomicReference<String> traceId = new AtomicReference<>("");

            return monoResult
                    .doOnSuccess(o -> {
                        if (!traceId.get().isEmpty()) {
                            MDC.put("X-B3-TraceId", traceId.get());
                        }
                        var response = "";
                        if (Objects.nonNull(o)) {
                            response = o.toString();
                        }
                        log.info("Enter: {}.{}() with argument[s] = {}",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs());
                        log.info("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs()[0],
                                response, (System.currentTimeMillis() - start));
                    })
                    .subscriberContext(context -> {
                        Context contextTmp = (Context) context;
                        if (contextTmp.hasKey("X-B3-TraceId")) {
                            traceId.set(contextTmp.get("X-B3-TraceId"));
                            MDC.put("X-B3-TraceId", contextTmp.get("X-B3-TraceId"));
                        }
                        return context;
                    })
                    .doOnError(o -> {
                        if (!traceId.get().isEmpty()) {
                            MDC.put("X-B3-TraceId", traceId.get());
                        }
                        log.info("Enter: {}.{}() with argument[s] = {}",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs());
                        log.error("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs()[0],
                                o.toString(), (System.currentTimeMillis() - start));
                    });
        } else if (result instanceof Flux) {
            var fluxResult = (Flux) result;

            return fluxResult
                    .doFinally(o -> {
                        log.info("Enter: {}.{}() with argument[s] = {}",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs());
                        log.info("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs()[0],
                                o.toString(), (System.currentTimeMillis() - start));
                    })
                    .doOnError(o -> {
                        log.info("Enter: {}.{}() with argument[s] = {}",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs());
                        log.error("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                                joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                                joinPoint.getArgs()[0],
                                o.toString(), (System.currentTimeMillis() - start));
                    });
        } else {
            log.warn("Body type is not Mono/Flux for {}.{}()",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());

            try {
                log.info("Enter: {}.{}() with argument[s] = {}",
                        joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                        joinPoint.getArgs());
                log.info("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                        joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                        joinPoint.getArgs()[0],
                        result, (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.info("Enter: {}.{}() with argument[s] = {}",
                        joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                        joinPoint.getArgs());
                log.error("Exit: {}.{}() had arguments = {}, with result = {}, Execution time = {} ms",
                        joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                        joinPoint.getArgs()[0],
                        e, (System.currentTimeMillis() - start));
            }

            return result;
        }

    }

}