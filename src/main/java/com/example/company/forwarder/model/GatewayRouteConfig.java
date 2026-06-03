package com.example.company.forwarder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("gateway_route_config")
public class GatewayRouteConfig {
    @Id
    private Long id;
    private String apiId;
    private String targetUrl;
    private String httpMethod;
    private Boolean isActive;
}