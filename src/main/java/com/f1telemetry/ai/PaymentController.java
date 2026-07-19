package com.f1telemetry.ai;

import com.f1telemetry.domain.User;
import com.f1telemetry.repository.UserRepository;
import com.f1telemetry.service.AiPricingService;
import com.f1telemetry.service.PreferenceService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    private final UserRepository userRepository;
    private final AiPricingService pricingService;
    private final PreferenceService preferenceService;

    @Autowired
    public PaymentController(UserRepository userRepository,
                             AiPricingService pricingService,
                             PreferenceService preferenceService) {
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.preferenceService = preferenceService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> data) {
        try {
            double usdAmount = Double.parseDouble(data.get("amount").toString());
            // Assume exchange rate of 1 USD = 85 INR
            double inrAmount = usdAmount * 85.0;
            int amountInPaise = (int) Math.round(inrAmount * 100.0);

            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = client.orders.create(orderRequest);

            return ResponseEntity.ok(Map.of(
                "orderId", order.get("id"),
                "amount", order.get("amount"),
                "currency", order.get("currency"),
                "keyId", keyId,
                "usdAmount", usdAmount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
        try {
            String paymentId = payload.get("razorpay_payment_id");
            String orderId = payload.get("razorpay_order_id");
            String signature = payload.get("razorpay_signature");
            double usdAmount = Double.parseDouble(payload.get("usdAmount"));

            JSONObject options = new JSONObject();
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);
            if (!isValid) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid signature verification"));
            }

            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            User user = userOpt.get();
            pricingService.addCredits(user, usdAmount);
            preferenceService.evictCache(username);

            // Fetch new preferences to get updated balance
            com.f1telemetry.domain.UserPreference prefs = preferenceService.getPreferences(username);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "newBalance", prefs.getCreditBalance()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
