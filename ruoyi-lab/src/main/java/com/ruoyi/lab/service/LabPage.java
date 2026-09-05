package com.ruoyi.lab.service;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;

/** Defers pagination until the business query, after authorization SQL has finished. */
public final class LabPage
{
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

    private LabPage() { }

    public static void prepare(String pageNum, String pageSize)
    {
        clear();
        int number = parse(pageNum, 1);
        int size = parse(pageSize, 10);
        if (number < 1 || size < 1 || size > 100)
        {
            throw invalid();
        }
        REQUEST.set(new Request(number, size));
    }

    public static <T> List<T> query(Supplier<List<T>> query)
    {
        Request request = REQUEST.get();
        if (request == null)
        {
            return query.get();
        }
        try
        {
            // Use the exact authorized query as a derived-table count. Smart SQL rewriting
            // can stall on the MySQL BINARY/EXISTS predicates used by object authorization.
            PageHelper.startPage(request.number(), request.size()).setReasonable(false).setKeepOrderBy(true);
            return query.get();
        }
        finally
        {
            PageHelper.clearPage();
        }
    }

    public static <T, R> List<R> query(Supplier<List<T>> query, Function<T, R> mapper)
    {
        List<T> source = query(query);
        if (source instanceof Page<?> page)
        {
            Page<R> result = new Page<>(page.getPageNum(), page.getPageSize());
            result.setTotal(page.getTotal());
            source.forEach(item -> result.add(mapper.apply(item)));
            return result;
        }
        return source.stream().map(mapper).toList();
    }

    public static void clear()
    {
        REQUEST.remove();
        PageHelper.clearPage();
    }

    private static int parse(String value, int fallback)
    {
        if (value == null) { return fallback; }
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw invalid(); }
    }

    private static LabBusinessException invalid()
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                "页码必须为正整数，每页条数必须为1到100的整数");
    }

    private record Request(int number, int size) { }
}
