package org.onlab.onos.metrics.intent.cli;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.karaf.shell.commands.Command;
import org.onlab.metrics.EventMetric;
import org.onlab.onos.cli.AbstractShellCommand;
import org.onlab.onos.metrics.intent.IntentMetricsService;

/**
 * Command to show the intent events metrics.
 */
@Command(scope = "onos", name = "intents-events-metrics",
         description = "Lists intent events metrics")
public class IntentEventsMetricsCommand extends AbstractShellCommand {

    private static final String FORMAT_GAUGE =
        "Intent %s Event Timestamp (ms from epoch)=%d";
    private static final String FORMAT_METER =
        "Intent %s Events count=%d rate(events/sec) mean=%f m1=%f m5=%f m15=%f";

    @Override
    protected void execute() {
        IntentMetricsService service = get(IntentMetricsService.class);

        if (outputJson()) {
            ObjectMapper mapper = new ObjectMapper()
                .registerModule(new MetricsModule(TimeUnit.SECONDS,
                                                  TimeUnit.MILLISECONDS,
                                                  false));
            ObjectNode result = mapper.createObjectNode();
            result = json(mapper, result, "intentSubmitted",
                          service.intentSubmittedEventMetric());
            result = json(mapper, result, "intentInstalled",
                          service.intentInstalledEventMetric());
            result = json(mapper, result, "intentWithdrawRequested",
                          service.intentWithdrawRequestedEventMetric());
            result = json(mapper, result, "intentWithdrawn",
                          service.intentWithdrawnEventMetric());
            print("%s", result);
        } else {
            printEventMetric("Submitted",
                             service.intentSubmittedEventMetric());
            printEventMetric("Installed",
                             service.intentInstalledEventMetric());
            printEventMetric("Withdraw Requested",
                             service.intentWithdrawRequestedEventMetric());
            printEventMetric("Withdrawn",
                             service.intentWithdrawnEventMetric());
        }
    }

    /**
     * Produces JSON node for an Event Metric.
     *
     * @param mapper the JSON object mapper to use
     * @param objectNode the JSON object node to use
     * @param propertyPrefix the property prefix to use
     * @param eventMetric the Event Metric with the data
     * @return JSON object node for the Event Metric
     */
    private ObjectNode json(ObjectMapper mapper, ObjectNode objectNode,
                            String propertyPrefix, EventMetric eventMetric) {
        String gaugeName = propertyPrefix + "Timestamp";
        String meterName = propertyPrefix + "Rate";
        Gauge<Long> gauge = eventMetric.lastEventTimestampGauge();
        Meter meter = eventMetric.eventRateMeter();

        objectNode.put(gaugeName, json(mapper, gauge));
        objectNode.put(meterName, json(mapper, meter));
        return objectNode;
    }

    /**
     * Produces JSON node for an Object.
     *
     * @param mapper the JSON object mapper to use
     * @param object the Object with the data
     * @return JSON node for the Object
     */
    private JsonNode json(ObjectMapper mapper, Object object) {
        //
        // NOTE: The API for custom serializers is incomplete,
        // hence we have to parse the JSON string to create JsonNode.
        //
        try {
            final String objectJson = mapper.writeValueAsString(object);
            JsonNode jsonNode = mapper.readTree(objectJson);
            return jsonNode;
        } catch (JsonProcessingException e) {
            log.error("Error writing value as JSON string", e);
        } catch (IOException e) {
            log.error("Error writing value as JSON string", e);
        }
        return null;
    }

    /**
     * Prints an Event Metric.
     *
     * @param operationStr the string with the intent operation to print
     * @param eventMetric the Event Metric to print
     */
    private void printEventMetric(String operationStr,
                                  EventMetric eventMetric) {
        Gauge<Long> gauge = eventMetric.lastEventTimestampGauge();
        Meter meter = eventMetric.eventRateMeter();
        TimeUnit rateUnit = TimeUnit.SECONDS;
        double rateFactor = rateUnit.toSeconds(1);

        // Print the Gauge
        print(FORMAT_GAUGE, operationStr, gauge.getValue());

        // Print the Meter
        print(FORMAT_METER, operationStr, meter.getCount(),
              meter.getMeanRate() * rateFactor,
              meter.getOneMinuteRate() * rateFactor,
              meter.getFiveMinuteRate() * rateFactor,
              meter.getFifteenMinuteRate() * rateFactor);
    }
}
