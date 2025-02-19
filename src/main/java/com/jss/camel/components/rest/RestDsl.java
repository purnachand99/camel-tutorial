package com.jss.camel.components.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jss.camel.dto.WeatherDto;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.support.DefaultMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static java.util.Objects.nonNull;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
@ConditionalOnProperty(name = "jss.camel.rest-dsl.enabled", havingValue = "true")
public class RestDsl extends RouteBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WeatherDataProvider weatherDataProvider;

    public RestDsl() {
        this.weatherDataProvider = new WeatherDataProvider();
    }

    static void getCity(Exchange exchange, WeatherDataProvider weatherDataProvider) {
        String city = exchange.getMessage().getHeader("city", String.class);
        WeatherDto currentWeather = weatherDataProvider.getCurrentWeather(city);

        if (nonNull(currentWeather)) {
            Message message = new DefaultMessage(exchange.getContext());
            message.setBody(currentWeather);
            exchange.setMessage(message);
        } else {
            exchange.getMessage().setHeader(HTTP_RESPONSE_CODE, NOT_FOUND.value());
        }
    }

    @Override
    public void configure() throws Exception {
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.auto);

        rest()
                .consumes("application/json").produces("application/json")
                .get("/weather/{city}").outType(WeatherDto.class).to("direct:get-weather-data")
                .post("/weather").type(WeatherDto.class).to("direct:save-weather-data");

        from("direct:get-weather-data")
                .process(this::getWeatherDataAndSetToExchange)
        ;

        from("direct:save-weather-data")
                .process(this::saveWeatherData)
                .end()
        ;
    }

    private void saveWeatherData(Exchange exchange) {
        try {
            WeatherDto body = exchange.getMessage().getBody(WeatherDto.class);
            this.weatherDataProvider.setCurrentWeather(body);
        } catch (Exception e) {
            log.error("Error While Saving. ", e);
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        }
    }

    private void getWeatherDataAndSetToExchange(Exchange exchange) {
        getCity(exchange, this.weatherDataProvider);
    }
}
