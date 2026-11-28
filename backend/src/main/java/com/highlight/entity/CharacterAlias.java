package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_alias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id")
	private BookCharacter character;

	@Column(nullable = false)
	private String alias;
}
