-- =====================================================================
-- V5: Durable notification outbox (retires TD-02, implements TD-08 / FR-APT-06)
-- Enqueued in the same transaction as the business change (outbox pattern);
-- drained by a scheduled worker with exponential backoff.
-- =====================================================================
CREATE TABLE notification_outbox (
  notification_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  template        varchar(40) NOT NULL,                     -- booking_confirmation | cancellation | reminder | payment_receipt
  ref_id          uuid NOT NULL,                            -- the business row this notification is about
  recipient       varchar(255) NOT NULL,
  subject         varchar(200) NOT NULL,
  body_text       text NOT NULL,
  status          text NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending','sent','failed','skipped')),
  attempts        int NOT NULL DEFAULT 0,
  last_error      text,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  created_at      timestamptz NOT NULL DEFAULT now(),
  sent_at         timestamptz,
  UNIQUE (template, ref_id)                                 -- idempotent enqueue: one email per event
);
CREATE INDEX idx_outbox_due ON notification_outbox (status, next_attempt_at);
