package com.ruoyi.lab.service;

import java.util.List;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LabPageTest
{
    @AfterEach
    void clear() { LabPage.clear(); }

    @Test
    void defersUntilQueryPreservesTotalAndClearsBeforeMapping()
    {
        LabPage.prepare("2", "2");
        assertThat(PageHelper.getLocalPage()).isNull();
        List<String> rows = LabPage.query(() -> {
            Page<Integer> page = PageHelper.getLocalPage();
            assertThat(page.getPageNum()).isEqualTo(2);
            assertThat(page.getPageSize()).isEqualTo(2);
            assertThat(page.getKeepOrderBy()).isTrue();
            page.setTotal(7);
            page.addAll(List.of(3, 4));
            return page;
        }, number -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return number.toString();
        });
        assertThat(rows).containsExactly("3", "4");
        assertThat(new PageInfo<>(rows).getTotal()).isEqualTo(7);
    }

    @Test
    void validatesBoundsAndAlwaysClearsFailedQuery()
    {
        for (String size : List.of("0", "-1", "101", "oops", "2147483648"))
        {
            assertThatThrownBy(() -> LabPage.prepare("1", size)).isInstanceOf(LabBusinessException.class);
        }
        assertThatThrownBy(() -> LabPage.prepare("0", "10")).isInstanceOf(LabBusinessException.class);
        LabPage.prepare("1", "100");
        assertThatThrownBy(() -> LabPage.query(() -> { throw new IllegalStateException(); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PageHelper.getLocalPage()).isNull();
    }
}
