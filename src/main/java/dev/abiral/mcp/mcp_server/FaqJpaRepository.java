package dev.abiral.mcp.mcp_server;

import dev.abiral.mcp.mcp_server.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqJpaRepository extends JpaRepository<Faq, Long> {

    // closest chunk by cosine distance (pgvector's <=> operator)
    @Query(value = "SELECT * FROM faq_chunks ORDER BY embedding <=> CAST(:vector AS vector) LIMIT 1", nativeQuery = true)
    Faq searchFaqVector(@Param("vector") float[] vector);
}
