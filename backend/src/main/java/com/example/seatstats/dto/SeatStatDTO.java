package com.example.seatstats.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatDTO {

    private Long routeId;

    private String routeCode;

    private String routeName;

    private String startPort;

    private String endPort;

    private Long seatCount;

    private String status;
}