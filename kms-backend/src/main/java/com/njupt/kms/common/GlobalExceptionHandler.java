package com.njupt.kms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务失败：HTTP 200 + 非零 code，message 可直接展示。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：拼出字段级提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "参数校验失败";
        }
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 请求体无法解析（JSON 语法错误、编码不是 UTF-8 等）。
     * 这是客户端问题，归入 PARAM_ERROR 而不是系统异常——
     * 否则用户看到"系统繁忙"会以为是服务端故障，无从下手。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR, "请求体格式错误，请检查 JSON 是否合法且为 UTF-8 编码");
    }

    /** 路径变量或查询参数类型不匹配，例如 /api/knowledge/abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={} value={}", e.getName(), e.getValue());
        return Result.fail(ResultCode.PARAM_ERROR, "参数 " + e.getName() + " 的格式不正确");
    }

    /** 缺少必填的查询参数或表单字段。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.fail(ResultCode.PARAM_ERROR, "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 上传文件超过 multipart 上限。
     *
     * <p>Spring 在请求进入 Controller **之前**就抛此异常，
     * 因此 LocalFileStorage 里的大小校验根本来不及执行。
     * 不在此处接住的话，用户传个大文件只会看到「系统繁忙」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR, "文件不能超过 20MB");
    }

    /**
     * 请求路径不存在。
     *
     * <p>Spring 6.1 起，未映射的路径抛 NoResourceFoundException。
     * 若落到下面的兜底处理器，一个拼错的 URL 会得到 HTTP 500 —— 显然是错的。
     * 路径不存在不是业务结果，返回真正的 404 更符合工具与监控的预期。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("路径不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在");
    }

    /**
     * 系统异常：完整堆栈只进日志，绝不回传前端。
     * 防止数据库连接串、密钥等内部信息通过错误信息泄露。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSystem(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }
}
