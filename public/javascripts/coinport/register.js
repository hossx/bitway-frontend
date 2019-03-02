var app = angular.module('register', []);

app.controller('RegisterCtrl', function ($scope, $http, $window) {
    $scope.errorMsg = '';
    $scope.doregister = function() {
        $http.post('/api/v1/register', {"password": $scope.password, "email": $scope.email})
            .success(function(data, status, headers, config) {
                console.log('data',data);
                $scope.email = "";
                $scope.password = "";
                $scope.confirmPassword = "";

                if (data.rv == true) {
                    $window.location.href = "/prompt?titleKey=register&msgKey=register.succeeded"
                } else {
                    $scope.errorMsg = data.msg;
                }
            });
    };
});

app.directive("repeatInput", function() {
    return {
        require: "ngModel",
        link: function(scope, elem, attrs, ctrl) {
            var otherInput = elem.inheritedData("$formController")[attrs.repeatInput];

            ctrl.$parsers.push(function(value) {
                if(value === otherInput.$viewValue) {
                    ctrl.$setValidity("repeat", true);
                    return value;
                }
                ctrl.$setValidity("repeat", false);
            });

            otherInput.$parsers.push(function(value) {
                ctrl.$setValidity("repeat", value === ctrl.$viewValue);
                return value;
            });
        }
    };
});
