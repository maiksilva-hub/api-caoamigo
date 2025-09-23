package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Adocao extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @NotNull(message = "A data da solicitação não pode ser nula")
    public LocalDate dataSolicitacao;

    @NotBlank(message = "A justificativa é obrigatória")
    @Size(max = 2000)
    public String justificativa;

    @NotBlank(message = "O status da adoção é obrigatório")
    @Size(max = 50)
    public String status; // Ex: Pendente, Aprovada, Rejeitada

    // Many-to-One: várias adoções para um cachorro
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cachorro_id")
    public Cachorro cachorro;

    // Many-to-Many: uma adoção pode envolver várias raças (se o cão for SRD)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "adocao_raca",
            joinColumns = @JoinColumn(name = "adocao_id"),
            inverseJoinColumns = @JoinColumn(name = "raca_id")
    )
    public Set<Raca> racas = new HashSet<>();

    public Adocao() {}

    public Adocao(Long id, LocalDate dataSolicitacao, String justificativa, String status) {
        this.id = id;
        this.dataSolicitacao = dataSolicitacao;
        this.justificativa = justificativa;
        this.status = status;
    }
}
