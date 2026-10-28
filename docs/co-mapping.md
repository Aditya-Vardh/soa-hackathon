# Team 9 CO mapping and honest status

| CO | Required evidence from the handout | Current scaffold |
|---|---|---|
| CO1 | Auth + 3 separate domain services, REST contracts, Kafka event workflow, event IDs/deduplication/replay/DLQ | Six modules, gateway REST routes, explicit attempt/feedback/entitlement APIs, and a working retry workflow exist. Kafka reliability remains open. |
| CO2 | Signed JWTs checked by Gateway and domain services, learner/labInstructor roles, record-level authorization, explicit CORS, TLS, redacted audit | Not implemented. |
| CO3 | Eureka registration, Gateway discovery-name routes, separate JPA stores/credentials, two instances with load balancing/recovery | Eureka registration, gateway routing, and local CORS are configured. Current data uses in-memory maps; durable stores and failover are not implemented. |
| CO4 | JUnit 5 + AssertJ, SpringBootTest, DataJpaTest, REST/event contract tests, repeatable load check, GitHub Actions | Entitlement JUnit/AssertJ rules, Maven reactor verification, and frontend production build are implemented. Integration, persistence, load, and CI evidence remain open. |

Required edge case: reserve a retry, cancel before start, and prove the token remains available.
