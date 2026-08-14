package com.medicore.payments;

import com.medicore.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Placeholder adapter for ITC Payments. Implemented at Milestone 3 once the API
 * specification is supplied (SRS OI-5): request/callback endpoints, authentication,
 * and callback verification mechanism.
 */
@Component
public class ItcGatewayAdapter implements PaymentGateway {
    private static final String PENDING =
        "ITC Payments integration pending API specification (SRS OI-5); scheduled for Milestone 3";

    @Override
    public PaymentInstruction requestPayment(PaymentRequest request) {
        throw new ApiException(501, PENDING);
    }

    @Override
    public VerificationResult verifyStatus(String gatewayReference) {
        throw new ApiException(501, PENDING);
    }
}
