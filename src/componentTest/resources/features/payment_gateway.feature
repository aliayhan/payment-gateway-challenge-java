Feature: Payment Gateway Docker Component Tests

  Scenario: Submit an authorized payment and retrieve it via Location header
    When a merchant submits a payment request with:
      """
      {
        "card_number": "2222405343240041",
        "expiry_month": 4,
        "expiry_year": 2028,
        "currency": "GBP",
        "amount": 1050,
        "cvv": "099"
      }
      """
    Then the response status should be 201
    And the response should include header "X-Request-Id"
    And the response should include header "Location"
    And the response JSON field "status" should be "Authorized"
    And the response JSON field "cardNumberLastFour" should be "0041"
    And the response JSON field "expiryMonth" should be 4
    And the response JSON field "expiryYear" should be 2028
    And the response JSON field "currency" should be "GBP"
    And the response JSON field "amount" should be 1050
    When the merchant retrieves the payment using the Location header
    Then the response status should be 200
    And the response should include header "X-Request-Id"
    And the response JSON should match the previously created payment

  Scenario: Submit a declined payment and retrieve it via Location header
    When a merchant submits a payment request with:
      """
      {
        "card_number": "2222405343240042",
        "expiry_month": 12,
        "expiry_year": 2028,
        "currency": "USD",
        "amount": 2500,
        "cvv": "123"
      }
      """
    Then the response status should be 201
    And the response should include header "X-Request-Id"
    And the response should include header "Location"
    And the response JSON field "status" should be "Declined"
    And the response JSON field "cardNumberLastFour" should be "0042"
    When the merchant retrieves the payment using the Location header
    Then the response status should be 200
    And the response JSON field "status" should be "Declined"
    And the response JSON field "cardNumberLastFour" should be "0042"

  Scenario: Bank simulator 503 returns 503 Service Unavailable
    When a merchant submits a payment request with:
      """
      {
        "card_number": "2222405343248870",
        "expiry_month": 12,
        "expiry_year": 2028,
        "currency": "EUR",
        "amount": 500,
        "cvv": "123"
      }
      """
    Then the response status should be 503
    And the response should include header "X-Request-Id"
    And the response should not include header "Location"
    And the response JSON field "message" should be "Acquiring bank is currently unavailable"

  Scenario: Invalid payment fields are rejected without leaking input values
    When a merchant submits a payment request with:
      """
      {
        "card_number": "4111BAD",
        "expiry_month": 13,
        "expiry_year": 2020,
        "currency": "JPY",
        "amount": 0,
        "cvv": "9"
      }
      """
    Then the response status should be 400
    And the response should include header "X-Request-Id"
    And the response JSON field "status" should be "Rejected"
    And the response JSON field "message" should be "Payment request validation failed"
    And the response body should not contain "4111BAD"
    And the response body should not contain "JPY"

  Scenario: Unknown payment UUID returns 404 ID not found
    When a merchant requests GET "/payment/550e8400-e29b-41d4-a716-446655440000"
    Then the response status should be 404
    And the response should include header "X-Request-Id"
    And the response JSON field "message" should be "ID not found"

  Scenario: Non-UUID payment ID returns 400 Bad Request
    When a merchant requests GET "/payment/not-a-uuid"
    Then the response status should be 400
    And the response should include header "X-Request-Id"
    And the response JSON field "message" should be "Invalid payment ID format; expected a UUID"

  Scenario: OpenAPI documentation is available
    When a merchant requests GET "/v3/api-docs"
    Then the response status should be 200
    And the response body should contain "/payment/{id}"
