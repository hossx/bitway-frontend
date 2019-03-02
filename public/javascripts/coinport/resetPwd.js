var app = angular.module('resetPwd', ['passwordCheck']);

app.controller('ResetPwdCtrl', function ($scope, $http, $window) {
    $scope.errorMsg = '';

    $scope.sendEmail = function() {
        console.log("email", $scope.email);
        $http.get('/requestpasswordreset', {params: {email: $scope.email}})
            .success(function(data, status, headers, config) {
                $window.location.href = "/prompt?titleKey=resetpwd&msgKey=pwdreset.request.succeeded";
            });
    };

    $scope.changePwd = function() {
        console.log("password"+$scope.password);
        $http.post('/dopwdreset', {"password": $scope.password, "token": $scope.token})
            .success(function(data, status, headers, config) {
                if (data.rv == true) {
                    $window.location.href = "/prompt?titleKey=resetpwd&msgKey=passwordreset.succeeded";
                } else {
                    $window.location.href = "/prompt?titleKey=resetpwd&msgKey="+data.msg;
                }
            });
    };
});

