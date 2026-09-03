package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LabSortWhitelistTest
{
    private final LabSortWhitelist whitelist = new LabSortWhitelist();

    @ParameterizedTest
    @MethodSource("validSorts")
    void resolvesOnlyMappedColumns(String resource, String key, String expectedColumn)
    {
        LabSortWhitelist.SortClause clause = whitelist.resolve(resource, key, "asc");

        assertThat(clause.column()).isEqualTo(expectedColumn);
        assertThat(clause.direction()).isEqualTo("ASC");
    }

    @Test
    void acceptsCaseInsensitiveDirections()
    {
        assertThat(whitelist.resolve("device", "name", "aSc").direction()).isEqualTo("ASC");
        assertThat(whitelist.resolve("device", "name", "DeSc").direction()).isEqualTo("DESC");
    }

    @ParameterizedTest
    @MethodSource("invalidSorts")
    void rejectsInvalidSortInput(String resource, String key, String direction)
    {
        assertThatThrownBy(() -> whitelist.resolve(resource, key, direction))
                .isInstanceOf(LabBusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LabErrorCode.VALIDATION_ERROR);
    }

    private static Stream<Arguments> validSorts()
    {
        return Stream.of(
                Arguments.of("laboratory", "name", "l.name"),
                Arguments.of("laboratory", "labCode", "l.lab_code"),
                Arguments.of("laboratory", "createTime", "l.create_time"),
                Arguments.of("device", "assetNo", "d.asset_no"),
                Arguments.of("device", "name", "d.name"),
                Arguments.of("device", "status", "d.status"),
                Arguments.of("device", "createTime", "d.create_time"),
                Arguments.of("qualification", "validUntil", "q.valid_until"),
                Arguments.of("qualification", "createTime", "q.create_time"));
    }

    private static Stream<Arguments> invalidSorts()
    {
        return Stream.of(
                Arguments.of(null, "name", "asc"),
                Arguments.of("device", null, "asc"),
                Arguments.of("device", "name", null),
                Arguments.of("unknown", "name", "asc"),
                Arguments.of("device", "unknown", "asc"),
                Arguments.of("device", "name desc", "asc"),
                Arguments.of("device", " createTime", "asc"),
                Arguments.of("device", "create_time", "asc"),
                Arguments.of("device", "updatexml(1,1,1)", "asc"),
                Arguments.of("device", "name", "ascending"),
                Arguments.of("device", "name", " desc"));
    }
}
