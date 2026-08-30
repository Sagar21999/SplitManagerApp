package com.splitmanager.api.dto;

import com.splitmanager.api.model.TransactionStatus;

public record StatusUpdateRequest(TransactionStatus status) {}
