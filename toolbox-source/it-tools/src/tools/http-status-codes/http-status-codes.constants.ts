export interface HttpStatusCode {
  code: number
  nameKey: string
  descriptionKey: string
  type: 'HTTP' | 'WebDav'
}

export const codesByCategories: { categoryKey: string; codes: HttpStatusCode[] }[] = [
  {
    categoryKey: 'tools.http-status-codes.ui.categories.informational',
    codes: [
      {
        code: 100,
        nameKey: 'tools.http-status-codes.ui.codes.100.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.100.description',
        type: 'HTTP',
      },
      {
        code: 101,
        nameKey: 'tools.http-status-codes.ui.codes.101.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.101.description',
        type: 'HTTP',
      },
      {
        code: 102,
        nameKey: 'tools.http-status-codes.ui.codes.102.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.102.description',
        type: 'WebDav',
      },
      {
        code: 103,
        nameKey: 'tools.http-status-codes.ui.codes.103.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.103.description',
        type: 'HTTP',
      },
    ],
  },
  {
    categoryKey: 'tools.http-status-codes.ui.categories.success',
    codes: [
      {
        code: 200,
        nameKey: 'tools.http-status-codes.ui.codes.200.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.200.description',
        type: 'HTTP',
      },
      {
        code: 201,
        nameKey: 'tools.http-status-codes.ui.codes.201.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.201.description',
        type: 'HTTP',
      },
      {
        code: 202,
        nameKey: 'tools.http-status-codes.ui.codes.202.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.202.description',
        type: 'HTTP',
      },
      {
        code: 203,
        nameKey: 'tools.http-status-codes.ui.codes.203.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.203.description',
        type: 'HTTP',
      },
      {
        code: 204,
        nameKey: 'tools.http-status-codes.ui.codes.204.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.204.description',
        type: 'HTTP',
      },
      {
        code: 205,
        nameKey: 'tools.http-status-codes.ui.codes.205.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.205.description',
        type: 'HTTP',
      },
      {
        code: 206,
        nameKey: 'tools.http-status-codes.ui.codes.206.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.206.description',
        type: 'HTTP',
      },
      {
        code: 207,
        nameKey: 'tools.http-status-codes.ui.codes.207.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.207.description',
        type: 'WebDav',
      },
      {
        code: 208,
        nameKey: 'tools.http-status-codes.ui.codes.208.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.208.description',
        type: 'WebDav',
      },
      {
        code: 226,
        nameKey: 'tools.http-status-codes.ui.codes.226.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.226.description',
        type: 'HTTP',
      },
    ],
  },
  {
    categoryKey: 'tools.http-status-codes.ui.categories.redirection',
    codes: [
      {
        code: 300,
        nameKey: 'tools.http-status-codes.ui.codes.300.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.300.description',
        type: 'HTTP',
      },
      {
        code: 301,
        nameKey: 'tools.http-status-codes.ui.codes.301.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.301.description',
        type: 'HTTP',
      },
      {
        code: 302,
        nameKey: 'tools.http-status-codes.ui.codes.302.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.302.description',
        type: 'HTTP',
      },
      {
        code: 303,
        nameKey: 'tools.http-status-codes.ui.codes.303.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.303.description',
        type: 'HTTP',
      },
      {
        code: 304,
        nameKey: 'tools.http-status-codes.ui.codes.304.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.304.description',
        type: 'HTTP',
      },
      {
        code: 305,
        nameKey: 'tools.http-status-codes.ui.codes.305.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.305.description',
        type: 'HTTP',
      },
      {
        code: 306,
        nameKey: 'tools.http-status-codes.ui.codes.306.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.306.description',
        type: 'HTTP',
      },
      {
        code: 307,
        nameKey: 'tools.http-status-codes.ui.codes.307.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.307.description',
        type: 'HTTP',
      },
      {
        code: 308,
        nameKey: 'tools.http-status-codes.ui.codes.308.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.308.description',
        type: 'HTTP',
      },
    ],
  },
  {
    categoryKey: 'tools.http-status-codes.ui.categories.clientError',
    codes: [
      {
        code: 400,
        nameKey: 'tools.http-status-codes.ui.codes.400.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.400.description',
        type: 'HTTP',
      },
      {
        code: 401,
        nameKey: 'tools.http-status-codes.ui.codes.401.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.401.description',
        type: 'HTTP',
      },
      {
        code: 402,
        nameKey: 'tools.http-status-codes.ui.codes.402.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.402.description',
        type: 'HTTP',
      },
      {
        code: 403,
        nameKey: 'tools.http-status-codes.ui.codes.403.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.403.description',
        type: 'HTTP',
      },
      {
        code: 404,
        nameKey: 'tools.http-status-codes.ui.codes.404.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.404.description',
        type: 'HTTP',
      },
      {
        code: 405,
        nameKey: 'tools.http-status-codes.ui.codes.405.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.405.description',
        type: 'HTTP',
      },
      {
        code: 406,
        nameKey: 'tools.http-status-codes.ui.codes.406.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.406.description',
        type: 'HTTP',
      },
      {
        code: 407,
        nameKey: 'tools.http-status-codes.ui.codes.407.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.407.description',
        type: 'HTTP',
      },
      {
        code: 408,
        nameKey: 'tools.http-status-codes.ui.codes.408.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.408.description',
        type: 'HTTP',
      },
      {
        code: 409,
        nameKey: 'tools.http-status-codes.ui.codes.409.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.409.description',
        type: 'HTTP',
      },
      {
        code: 410,
        nameKey: 'tools.http-status-codes.ui.codes.410.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.410.description',
        type: 'HTTP',
      },
      {
        code: 411,
        nameKey: 'tools.http-status-codes.ui.codes.411.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.411.description',
        type: 'HTTP',
      },
      {
        code: 412,
        nameKey: 'tools.http-status-codes.ui.codes.412.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.412.description',
        type: 'HTTP',
      },
      {
        code: 413,
        nameKey: 'tools.http-status-codes.ui.codes.413.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.413.description',
        type: 'HTTP',
      },
      {
        code: 414,
        nameKey: 'tools.http-status-codes.ui.codes.414.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.414.description',
        type: 'HTTP',
      },
      {
        code: 415,
        nameKey: 'tools.http-status-codes.ui.codes.415.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.415.description',
        type: 'HTTP',
      },
      {
        code: 416,
        nameKey: 'tools.http-status-codes.ui.codes.416.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.416.description',
        type: 'HTTP',
      },
      {
        code: 417,
        nameKey: 'tools.http-status-codes.ui.codes.417.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.417.description',
        type: 'HTTP',
      },
      {
        code: 418,
        nameKey: 'tools.http-status-codes.ui.codes.418.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.418.description',
        type: 'HTTP',
      },
      {
        code: 421,
        nameKey: 'tools.http-status-codes.ui.codes.421.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.421.description',
        type: 'HTTP',
      },
      {
        code: 422,
        nameKey: 'tools.http-status-codes.ui.codes.422.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.422.description',
        type: 'HTTP',
      },
      {
        code: 423,
        nameKey: 'tools.http-status-codes.ui.codes.423.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.423.description',
        type: 'HTTP',
      },
      {
        code: 424,
        nameKey: 'tools.http-status-codes.ui.codes.424.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.424.description',
        type: 'HTTP',
      },
      {
        code: 425,
        nameKey: 'tools.http-status-codes.ui.codes.425.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.425.description',
        type: 'HTTP',
      },
      {
        code: 426,
        nameKey: 'tools.http-status-codes.ui.codes.426.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.426.description',
        type: 'HTTP',
      },
      {
        code: 428,
        nameKey: 'tools.http-status-codes.ui.codes.428.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.428.description',
        type: 'HTTP',
      },
      {
        code: 429,
        nameKey: 'tools.http-status-codes.ui.codes.429.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.429.description',
        type: 'HTTP',
      },
      {
        code: 431,
        nameKey: 'tools.http-status-codes.ui.codes.431.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.431.description',
        type: 'HTTP',
      },
      {
        code: 451,
        nameKey: 'tools.http-status-codes.ui.codes.451.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.451.description',
        type: 'HTTP',
      },
    ],
  },
  {
    categoryKey: 'tools.http-status-codes.ui.categories.serverError',
    codes: [
      {
        code: 500,
        nameKey: 'tools.http-status-codes.ui.codes.500.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.500.description',
        type: 'HTTP',
      },
      {
        code: 501,
        nameKey: 'tools.http-status-codes.ui.codes.501.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.501.description',
        type: 'HTTP',
      },
      {
        code: 502,
        nameKey: 'tools.http-status-codes.ui.codes.502.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.502.description',
        type: 'HTTP',
      },
      {
        code: 503,
        nameKey: 'tools.http-status-codes.ui.codes.503.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.503.description',
        type: 'HTTP',
      },
      {
        code: 504,
        nameKey: 'tools.http-status-codes.ui.codes.504.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.504.description',
        type: 'HTTP',
      },
      {
        code: 505,
        nameKey: 'tools.http-status-codes.ui.codes.505.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.505.description',
        type: 'HTTP',
      },
      {
        code: 506,
        nameKey: 'tools.http-status-codes.ui.codes.506.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.506.description',
        type: 'HTTP',
      },
      {
        code: 507,
        nameKey: 'tools.http-status-codes.ui.codes.507.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.507.description',
        type: 'HTTP',
      },
      {
        code: 508,
        nameKey: 'tools.http-status-codes.ui.codes.508.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.508.description',
        type: 'HTTP',
      },
      {
        code: 510,
        nameKey: 'tools.http-status-codes.ui.codes.510.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.510.description',
        type: 'HTTP',
      },
      {
        code: 511,
        nameKey: 'tools.http-status-codes.ui.codes.511.name',
        descriptionKey: 'tools.http-status-codes.ui.codes.511.description',
        type: 'HTTP',
      },
    ],
  },
];
