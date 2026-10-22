package io.casehub.agency.summarisation.examples.logistics;

public record PackageScan(String scanId, String warehouseId, double weight, String destination, ScanType scanType) {}
