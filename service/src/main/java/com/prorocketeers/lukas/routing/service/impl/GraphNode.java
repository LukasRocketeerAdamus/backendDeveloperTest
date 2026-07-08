package com.prorocketeers.lukas.routing.service.impl;

import java.util.List;

public record GraphNode(String id, List<String> neighbors) {
}