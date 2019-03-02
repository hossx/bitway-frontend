var app = angular.module('withdraw', ['pay.app', 'ui.bootstrap']);

app.controller('WithdrawalCtrl', ['$scope', '$http', '$interval', function ($scope, $http, $interval) {
    $scope.withdrawalData = {};
    $scope.balance = 0.0;
    $scope.withdrawInvalid = false;

    $scope.getBalance = function() {
        $http.get('/api/v1/balance/')
            .success(function(data, status, headers, config) {
                data.balances.forEach(function(entry) {
                    if (entry.currency == $scope.currency) $scope.balance = entry.balance;
                });
                updateWithdrawInvalid();
            });
    };

    $scope.getBalance();

    $scope.getUserStatus = function() {
        $http.get('/isuseractive').success(function(data, status, headers, config) {
            $scope.userStatus = data;
        });
    };

    $scope.getUserStatus();

    $scope.getAccountNumbers = function() {
        $http.get('/getaccountnumbers/' + $scope.currency)
            .success(function(data, status, headers, config) {
                if (data.withdrawAddress.size == 0)
                    $scope.accountNumber  = "";
                else
                    $scope.accountNumber  = data.withdrawAddress[0];
            });
    };

    var updateWithdrawInvalid = function() {
        if ($scope.currency.toUpperCase() == "CNY") {
            if ($scope.balance > 100) $scope.withdrawInvalid = false;
            else {
                $scope.withdrawErrorMessage = "无法提现： 您的人民币余额需要大于100元";
                $scope.withdrawInvalid = true;
            }
        } else if ($scope.currency.toUpperCase() == "USD" ) {
            if ($scope.balance > 10) $scope.withdrawInvalid = false;
            else {
                $scope.withdrawErrorMessage = "无法提现： 您的美元余额需要大于10美元";
                $scope.withdrawInvalid = true;
            }
        } else {
            $scope.withdrawInvalid = true;
        }
    };


    console.log("$scope.invalidBalance", $scope.invalidBalance);

    $scope.getAccountNumbers();



    $scope.page = 1;
    $scope.limit = 10;

    $scope.loadWithdrawals = function () {
    $http.get('/api/v1/querywithdrawal/' + $scope.currency , {params: {limit: $scope.limit, skip: $scope.limit*($scope.page-1), 'type': 1}})
            .success(function (data, status, headers, config) {
                $scope.withdrawals = data.items;
                $scope.count = data.count;
            });
    };

    $scope.loadWithdrawals();

    $scope.withdrawalData = {currency: $scope.currency, emailuuid: "empty"};
    $scope.withdrawal = function () {
        $scope.withdrawalData.address = $scope.accountNumber;
        $http.post('/api/v1/withdraw/', {data: $scope.withdrawalData})
            .success(function (data, status, headers, config) {
                if (data.success) {
                    alert("提交成功");
                } else {
                    alert("提交失败，错误代码为："+data.error);
                }
                setTimeout($scope.getBalance(), 1000);
                setTimeout($scope.loadWithdrawals(), 1000);
                setTimeout($scope.clearData(), 100);
            });
    };

    $scope.clearData = function () {
        $scope.stopTimingEmail();
        $scope.withdrawalData.emailcode = undefined;
        $scope.withdrawalData.amount = undefined;
    };

    $scope.cancelWithdrawal = function (item) {
        item.currency = $scope.currency;
        console.log(item);
        $http.post('/api/v1/cancelWithdrawal/', {data: item})
            .success(function (data, status, headers, config) {
                $scope.balance += item.amount;
                setTimeout($scope.getBalance(), 1000);
                setTimeout($scope.loadWithdrawals(), 1000);
                setTimeout($scope.clearData(), 100);
            });
    };

    $scope.changeCurrency = function() {
        $location.path('/withdrawal/' + $scope.currency);
    };

    // email verification code and button timer:
    $scope.showWithdrawalError = false;
    $scope.verifyButtonEmail = "获取邮件验证码";
    var _stop;
    $scope.isTimingEmail = false;

    $scope.disableButtonEmail = function () {
        if (angular.isDefined(_stop)) {
            $scope.isTimingEmail = true;
            return;
        }

        $scope.secondsEmail = 120;

        _stop = $interval(function () {
            if ($scope.secondsEmail > 0) {
                $scope.secondsEmail = $scope.secondsEmail - 1;
                $scope.verifyButtonEmail = ' '+ $scope.secondsEmail + '秒后重新获取邮件验证码';
                $scope.isTimingEmail = true;
            }
            else {
                $scope.stopTimingEmail();
                $scope.verifyButtonEmail = "获取邮件验证码";
            }
        }, 1000);
    };

    $scope.stopTimingEmail = function () {
        if (angular.isDefined(_stop)) {
            $interval.cancel(_stop);
            _stop = undefined;
        }
        $scope.isTimingEmail = false;
        $scope.secondsEmail = 0;
        $scope.verifyButtonEmail = "获取邮件验证码";
    };

    $scope.$on('destroy', function () {
        $scope.stopTimingEmail();
    });

    $scope.sendVerifyEmail = function () {
        $scope.showWithdrawalError = false;
        $scope.disableButtonEmail();

        $http.get('/sendverificationemail')
            .success(function (data, status, headers, config) {
                $scope.withdrawalData.emailuuid = data.uuid;
            });
    };
}]);
