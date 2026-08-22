package com.tikitaka.note.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tikitaka.global.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "private_strokes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivateStroke extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layer_id", nullable = false)
    private PrivateLayer layer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StrokeTool tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Double>> points;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String color;

    private Double thickness;

    @Column(nullable = false)
    private Double opacity = 1.0;

    @Column(name = "stroke_order", nullable = false)
    private Integer strokeOrder;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    private PrivateStroke(
            PrivateLayer layer,
            StrokeTool tool,
            List<Map<String, Double>> points,
            String content,
            String color,
            Double thickness,
            Double opacity,
            Integer strokeOrder
    ) {
        this.layer = layer;
        this.tool = tool;
        this.points = points;
        this.content = content;
        this.color = color;
        this.thickness = thickness;
        this.opacity = opacity;
        this.strokeOrder = strokeOrder;
    }

    public static PrivateStroke create(
            PrivateLayer layer,
            StrokeTool tool,
            List<Map<String, Double>> points,
            String content,
            String color,
            Double thickness,
            Double opacity,
            Integer strokeOrder
    ) {
        return new PrivateStroke(
                layer,
                tool,
                points,
                content,
                color,
                thickness,
                opacity,
                strokeOrder
        );
    }

    public void delete() {
        this.deleted = true;
    }
}