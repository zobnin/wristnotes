export default function(global, globalThis, window, $app_exports$, $app_evaluate$) {
    var org_app_require = $app_require$;
    (function(global, globalThis, window, $app_exports$, $app_evaluate$) {
        var setTimeout = global.setTimeout;
        var setInterval = global.setInterval;
        var clearTimeout = global.clearTimeout;
        var clearInterval = global.clearInterval;
        var $app_require$1 = global.$app_require$ || org_app_require;
        var createPageHandler = function() {
            return (()=>{
                var __webpack_modules__ = {};
                var __webpack_module_cache__ = {};
                function __webpack_require__(moduleId) {
                    var cachedModule = __webpack_module_cache__[moduleId];
                    if (void 0 !== cachedModule) return cachedModule.exports;
                    var module = __webpack_module_cache__[moduleId] = {
                        exports: {}
                    };
                    __webpack_modules__[moduleId](module, module.exports, __webpack_require__);
                    return module.exports;
                }
                (()=>{
                    __webpack_require__.rv = ()=>"1.7.12";
                })();
                (()=>{
                    __webpack_require__.ruid = "bundler=rspack@1.7.12";
                })();
                var $app_style$ = [
                    [
                        [
                            [
                                0,
                                "page"
                            ]
                        ],
                        {
                            width: "100%",
                            height: "100%",
                            backgroundColor: "#000000",
                            flexDirection: "column"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "text-scroll"
                            ]
                        ],
                        {
                            width: "100%",
                            height: "100%",
                            paddingTop: "36px",
                            paddingRight: "36px",
                            paddingBottom: "36px",
                            paddingLeft: "36px",
                            flexDirection: "column"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "document"
                            ]
                        ],
                        {
                            width: "100%",
                            flexDirection: "column"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "content"
                            ]
                        ],
                        {
                            width: "100%",
                            color: "#ffffff",
                            fontSize: "52px",
                            lineHeight: "68px",
                            lines: -1,
                            flexShrink: 0,
                            marginBottom: "20px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "heading1"
                            ]
                        ],
                        {
                            fontSize: "72px",
                            lineHeight: "86px",
                            fontWeight: "bold",
                            marginBottom: "28px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "heading2"
                            ]
                        ],
                        {
                            fontSize: "66px",
                            lineHeight: "80px",
                            fontWeight: "bold",
                            marginBottom: "24px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "heading3"
                            ]
                        ],
                        {
                            fontSize: "60px",
                            lineHeight: "74px",
                            fontWeight: "bold",
                            marginBottom: "22px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "list-item"
                            ]
                        ],
                        {
                            marginBottom: "12px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "quote"
                            ]
                        ],
                        {
                            color: "#b8c0cc",
                            fontStyle: "italic"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "code-block"
                            ]
                        ],
                        {
                            color: "#ffd166",
                            backgroundColor: "#171b24",
                            borderRadius: "12px",
                            paddingTop: "16px",
                            paddingRight: "16px",
                            paddingBottom: "16px",
                            paddingLeft: "16px",
                            fontSize: "46px",
                            lineHeight: "62px"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "rule"
                            ]
                        ],
                        {
                            color: "#7e8796",
                            textAlign: "center"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "strong"
                            ]
                        ],
                        {
                            fontWeight: "bold"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "emphasis"
                            ]
                        ],
                        {
                            fontStyle: "italic"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "deleted"
                            ]
                        ],
                        {
                            textDecoration: "line-through"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "code"
                            ]
                        ],
                        {
                            color: "#ffd166"
                        }
                    ],
                    [
                        [
                            [
                                0,
                                "link"
                            ]
                        ],
                        {
                            color: "#72a7ff",
                            textDecoration: "underline"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:circle)"
                        },
                        [
                            [
                                0,
                                "text-scroll"
                            ]
                        ],
                        {
                            paddingLeft: "72px",
                            paddingRight: "72px",
                            paddingTop: "72px",
                            paddingBottom: "72px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "text-scroll"
                            ]
                        ],
                        {
                            paddingLeft: "24px",
                            paddingRight: "24px",
                            paddingTop: "144px",
                            paddingBottom: "144px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "content"
                            ]
                        ],
                        {
                            fontSize: "60px",
                            lineHeight: "78px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "heading1"
                            ]
                        ],
                        {
                            fontSize: "76px",
                            lineHeight: "90px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "heading2"
                            ]
                        ],
                        {
                            fontSize: "72px",
                            lineHeight: "86px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "heading3"
                            ]
                        ],
                        {
                            fontSize: "66px",
                            lineHeight: "80px"
                        }
                    ],
                    [
                        {
                            condition: "screen and (shape:pill-shaped)"
                        },
                        [
                            [
                                0,
                                "code-block"
                            ]
                        ],
                        {
                            fontSize: "54px",
                            lineHeight: "72px"
                        }
                    ]
                ];
                var $app_script$ = function __scriptModule__(module, exports, $app_require$1) {
                    "use strict";
                    Object.defineProperty(exports, "__esModule", {
                        value: true
                    });
                    exports.default = void 0;
                    var _default = exports.default = {
                        private: {
                            blocks: "__RPKER_MARKDOWN_BLOCKS__"
                        }
                    };
                    const moduleOwn = exports.default || module.exports;
                    const accessors = [
                        'public',
                        'protected',
                        'private'
                    ];
                    if (moduleOwn.data && accessors.some(function(acc) {
                        return moduleOwn[acc];
                    })) throw new Error('页面VM对象中的属性data不可与"' + accessors.join(',') + '"同时存在，请使用private替换data名称');
                    if (!moduleOwn.data) {
                        moduleOwn.data = {};
                        moduleOwn._descriptor = {};
                        accessors.forEach(function(acc) {
                            const accType = typeof moduleOwn[acc];
                            if ('object' === accType) {
                                moduleOwn.data = Object.assign(moduleOwn.data, moduleOwn[acc]);
                                for(const name in moduleOwn[acc])moduleOwn._descriptor[name] = {
                                    access: acc
                                };
                            } else if ('function' === accType) console.warn('页面VM对象中的属性' + acc + '的值不能是函数，请使用对象');
                        });
                    }
                };
                var $app_template$ = function(vm) {
                    const _vm_ = vm || this;
                    return aiot.__ce__("div", {
                        __vm__: _vm_,
                        __opts__: {
                            classList: [
                                "page"
                            ]
                        }
                    }, [
                        aiot.__ce__("scroll", {
                            __vm__: _vm_,
                            __opts__: {
                                classList: [
                                    "text-scroll"
                                ],
                                scrollY: "true",
                                bounces: "true"
                            }
                        }, [
                            aiot.__ce__("div", {
                                __vm__: _vm_,
                                __opts__: {
                                    classList: [
                                        "document"
                                    ]
                                }
                            }, [
                                aiot.__cf__({
                                    __vm__: _vm_,
                                    __opts__: {
                                        exp: function() {
                                            return _vm_.blocks;
                                        },
                                        key: "$idx",
                                        value: "$item"
                                    }
                                }, function($idx, $item) {
                                    return [
                                        aiot.__ce__("text", {
                                            __vm__: _vm_,
                                            __opts__: {
                                                classList: function() {
                                                    const $classValue$ = "content " + $item.type;
                                                    if ('string' == typeof $classValue$) return $classValue$.split(' ').map((item)=>item.trim()).filter(Boolean);
                                                    return $classValue$;
                                                }
                                            }
                                        }, [
                                            aiot.__cf__({
                                                __vm__: _vm_,
                                                __opts__: {
                                                    exp: function() {
                                                        return $item.segments;
                                                    },
                                                    key: "$idx",
                                                    value: "$item"
                                                }
                                            }, function($idx, $item) {
                                                return [
                                                    aiot.__ce__("span", {
                                                        __vm__: _vm_,
                                                        __opts__: {
                                                            classList: function() {
                                                                const $classValue$ = $item.style;
                                                                if ('string' == typeof $classValue$) return $classValue$.split(' ').map((item)=>item.trim()).filter(Boolean);
                                                                return $classValue$;
                                                            },
                                                            value: function() {
                                                                return $item.text;
                                                            }
                                                        }
                                                    }, [])
                                                ];
                                            })
                                        ])
                                    ];
                                })
                            ])
                        ])
                    ]);
                };
                $app_exports$['entry'] = function($app_exports$) {
                    $app_script$({}, $app_exports$, $app_require$1);
                    $app_exports$.default.template = $app_template$;
                    $app_exports$.default.style = $app_style$;
                };
            })();
        };
        return createPageHandler();
    })(global, globalThis, window, $app_exports$, $app_evaluate$);
}
