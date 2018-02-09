package com.acedemand.autoletics;

import org.jinspired.probes.Probes;
import org.jinspired.probes.interceptor.*;
import org.jinspired.probes.Probes.*;
import io.opentracing.*;
import io.opentracing.util.GlobalTracer;
import com.uber.jaeger.Configuration;

public class OpenTracingProbesInterceptorFactory implements ProbesInterceptorFactory {
    public void init(Environment environment) {
        GlobalTracer.register(
                new Configuration(
                        "your_service_name",
                        new Configuration.SamplerConfiguration("const", 1),
                        new Configuration.ReporterConfiguration(
                                false, "localhost", null, 1000, 10000)
                ).getTracer());
    }

    public ProbesInterceptor create(Context context) {
        return new Interceptor(context, GlobalTracer.get());
    }

    public static void main(String[] args) {
        OpenTracingProbesInterceptorFactory a = new OpenTracingProbesInterceptorFactory();

    }

    private static final class Interceptor implements ProbesInterceptor {
        static final Name CLOCK_TIME = Probes.name("clock").name("time");

        final Tracer tracer;
        final Context context;

        Probe root;
        Span span;
        Probe leaf;

        private Interceptor(Context context, Tracer tracer) {
            this.context = context;
            this.tracer = tracer;
        }

        @Override
        public void begin(final Probe probe) {
            if (root == null) {
                span = tracer.buildSpan(probe.getName().toString())
                        .withStartTimestamp(probe.reading(CLOCK_TIME).getHigh())
                        .start();
                root = probe;
            }
            leaf = probe;
        }

        @Override
        public void end(final Probe probe) {
            if (probe == root) {
                span.finish(probe.reading(CLOCK_TIME).getHigh());

                span = null;
                root = null;
            } else if (probe == leaf) {
                final Reading r = probe.reading(CLOCK_TIME);
                GlobalTracer.get().buildSpan(probe.getName().toString())
                        .withStartTimestamp(r.getLow())
                        .asChildOf(span)
                        .start()
                        .finish(r.getHigh());
            }
        }
    }
}
