var app = angular.module('account', []);

app.controller('AccountCtrl', function ($scope, $http) {
    $scope.getBalance = function() {
        $http.get('/api/v1/balance/')
            .success(function(data, status, headers, config) {
                $scope.balanceMap = data.balances;
            });
    };
    $scope.getBalance();
});

app.controller('AccountProfilesCtrl', function($scope, $http) {
    $scope.contact = {};
    $scope.bankaccount = {};
    $scope.apiToken = {};

    $scope.getAccountSetting = function () {
        $http.get('/accountprofile/get/'+$scope.merchantId)
            .success(function(data, status, headers, config) {
                console.log("data", data);
                $scope.contact = data.merchant.contact;
                $scope.bankaccount = data.merchant.bankaccount;
                $scope.token = data.merchant.token;
            });
    };

    $scope.addToken = function () {
        $http.post('/accountprofile/createapitoken', {})
            .success(function(data, status, headers, config) {
                console.log("add token");
                $scope.getAccountSetting;
            });
    };

    $scope.deleteToken = function (token) {
        $http.post('/accountprofile/removeapitoken', {"token": token})
            .success(function(data, status, headers, config) {
                console.log("delete token");
                $scope.getAccountSetting;
            });
    };

    $scope.getAccountSetting();
});
