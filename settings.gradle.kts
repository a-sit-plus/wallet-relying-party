rootProject.name = "terminal_sp_server"

includeBuild("kmm-vc-library"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:vclib")).using(project(":vclib"))
        substitute(module("at.asitplus.wallet:vclib-aries")).using(project(":vclib-aries"))
        substitute(module("at.asitplus.wallet:vclib-openid")).using(project(":vclib-openid"))
    }
}