package com.example.company.forwarder.persistence.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * API mà đối tác gọi được: apiId trong body request trỏ tới URL của API Gateway (route *-fwd), không trỏ thẳng
 * vào service. target_url có thể chứa biến {ten}, lấy từ trường cùng tên trong body của đối tác, ví dụ
 * http://localhost:8081/customer-fwd/v1/customers/{cif}.
 */
@Table("gateway_route_config")
public class GatewayRouteConfig {

    @Id
    private Long id;
    private String apiId;
    private String targetUrl;
    /** GET, POST, PUT, PATCH, DELETE */
    private String httpMethod;
    private Boolean isActive;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
