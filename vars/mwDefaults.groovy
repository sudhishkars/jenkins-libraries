import groovy.transform.Field



// @Field Map FMBO_Env_Mappings = [
//     DEV: "RTF-DC2-NON-PROD",
//     QA: ["RTF-DC2-NON-PROD", "RTF-DC3-NON-PROD"],
//     TRN: ["RTF-DC3-NON-PROD"]
// ]


@Field Map portFolio_Env_Mappings = [
    ACME: [
        dev1: ["os-rtf-1"]
    ],
    ACME1: [
        dev1: ["os-rtf-1"]
    ]
]

@Field Map portFolio_Org_Mappings = [
    ACME: "27fe0a56-db9b-4689-9aa6-e91a7905d6a9",
    ACME1: "ac2ba1fb-3c45-4795-9e80-742f003efa18"
]

// @Field Map ACME1_ENV_Mappings = [
//     DEV1: "23b1b0a9-8dd0-426d-b3e5-308f7c867b1e"
// ]

// @Field Map ACME_ENV_Mappings = [
//     DEV1: "23b1b0a9-8dd0-426d-b3e5-308f7c867b1e"
// ]

@Field Map DEV1_Resource_Defaults = [
    cpu_reserved: "20m",
    cpu_limit: "200m",
    memory_reserved: "1000Mi",
    replicas: 2
]

@Field Map deployment_Params_Defaults = [
    anypoint_url: "https://anypoint.mulesoft.com",
    muleVersion: "4.4.0",
    provider: "MC",
    clustered: "false",
    lastMileSecurity: "false",
    forwardSSLSession: "false",
    enforceReplicasAcrossNodes: "true",
    skipDeployVerification: "true",
    updateStrategy: "rolling",
    persistentObjectStore: "false",
    analytics_agent_header_injection_disabled: "false"
]