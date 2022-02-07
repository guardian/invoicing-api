package com.gu.invoicing.common

import scalaj.http.{BaseHttp, HttpOptions}

object Http
    extends BaseHttp(
      options = Seq(
        HttpOptions.connTimeout(5000),
        HttpOptions.readTimeout(5 * 60 * 1000),
        HttpOptions.followRedirects(false)
      )
    )
