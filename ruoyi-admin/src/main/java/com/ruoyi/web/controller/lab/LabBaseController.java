package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.lab.service.LabPage;

/** Lab endpoints paginate only their final, authorized business query. */
public abstract class LabBaseController extends BaseController
{
    @Override
    protected void startPage()
    {
        LabPage.prepare(ServletUtils.getParameter("pageNum"), ServletUtils.getParameter("pageSize"));
    }

    @Override
    protected void clearPage()
    {
        LabPage.clear();
    }
}
