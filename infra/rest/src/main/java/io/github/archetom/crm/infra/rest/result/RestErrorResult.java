package io.github.archetom.crm.infra.rest.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author hanfeng
 */
@Data
public class RestErrorResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 2356702836088252228L;

    /**
     * error code
     * ErrorEnum in of code
     */
    private String errCode;

    /**
     * error message
     * can define message override ErrorEnum in of message
     */
    private String errMsg;
}
