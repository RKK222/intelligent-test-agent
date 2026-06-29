import zhCn from "element-plus/es/locale/lang/zh-cn";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";

// 把 dayjs 全局 locale 切到中文，让 Element Plus 的 el-date-picker（包括 type="month"）
// 在面板里显示中文"1月、2月、…、12月"。
// 项目里没有其他直接 dayjs 用法，所以全局切换是安全的。
dayjs.locale("zh-cn");

// 基于 Element Plus 自带的 zh-cn locale 做最小改动：把 month-picker 单元格里
// 默认的"一月、二月、…"改成"1月、2月、…、12月"（阿拉伯数字 + 月）。
// 其它文案（按钮、星期、占位符等）保持 zh-cn 原始翻译不变。
export const zhCnWithArabicMonths: typeof zhCn = {
  ...zhCn,
  el: {
    ...zhCn.el,
    datepicker: {
      ...zhCn.el.datepicker,
      months: {
        jan: "1月",
        feb: "2月",
        mar: "3月",
        apr: "4月",
        may: "5月",
        jun: "6月",
        jul: "7月",
        aug: "8月",
        sep: "9月",
        oct: "10月",
        nov: "11月",
        dec: "12月"
      }
    }
  }
};
