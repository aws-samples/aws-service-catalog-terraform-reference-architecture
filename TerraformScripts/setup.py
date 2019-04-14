import os
from setuptools import setup

setup(
    name="sc_terraform_wrapper",
    version="1.2",
    description="Python module to download and apply Terraform configurations",
    license="Apache License 2.0",
    install_requires=[
        'boto3 ~= 1.7.27',
        'python-dateutil ~= 2.7.3',
        'pyjq ~= 2.1.0',
        'requests >= 2.20.0'
    ],

    packages=["sc_terraform_wrapper"],
    package_data={"sc_terraform_wrapper" : ["LICENSE", "NOTICE"]},

    entry_points={
        'console_scripts': [
            'sc-terraform-wrapper = sc_terraform_wrapper.__main__:main',
            'install-terraform = sc_terraform_wrapper.terraform_installer:install_latest_terraform'
        ]
    },

    classifiers=[
        'Development Status :: 4 - Beta',
        'Environment :: Console',
        'Intended Audience :: Developers',
        'Intended Audience :: Information Technology',
        'License :: OSI Approved :: Apache Software License',
        'Programming Language :: Python',
        'Programming Language :: Python :: 3.4',
        'Topic :: Internet',
        'Topic :: Utilities'
    ]
)
