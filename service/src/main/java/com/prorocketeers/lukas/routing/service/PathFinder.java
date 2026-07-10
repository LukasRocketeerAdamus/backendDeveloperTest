package com.prorocketeers.lukas.routing.service;

import java.util.List;

public interface PathFinder {
    List<String> findPath(String origin, String destination);
}
