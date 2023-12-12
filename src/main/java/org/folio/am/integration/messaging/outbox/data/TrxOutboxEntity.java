package org.folio.am.integration.messaging.outbox.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "trx_outbox")
public class TrxOutboxEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trx_outbox_generator")
  @SequenceGenerator(name = "trx_outbox_generator", sequenceName = "seq_trx_outbox", allocationSize = 20)
  private Long id;

  @Column(name = "messageId", nullable = false)
  private UUID messageId;

  @Column(name = "destination", nullable = false)
  private String destination;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "created", updatable = false)
  private OffsetDateTime created;
}
